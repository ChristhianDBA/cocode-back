CREATE OR REPLACE FUNCTION fn_vecino(p_usuario uuid, p_exigir_cuadrante boolean)
RETURNS domicilios
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
DECLARE
    v_domicilio domicilios%ROWTYPE;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM usuarios WHERE id = p_usuario AND activo AND tipo = 'VECINO') THEN
        PERFORM fn_error(403, 'SOLO_VECINOS', 'Esta opción es solo para vecinos verificados');
    END IF;
    SELECT * INTO v_domicilio FROM domicilios WHERE usuario_id = p_usuario AND vigente;
    IF p_exigir_cuadrante AND v_domicilio.cuadrante_id IS NULL THEN
        PERFORM fn_error(409, 'SIN_COBERTURA', 'Tu zona aún no tiene cobertura');
    END IF;
    RETURN v_domicilio;
END
$$;

CREATE OR REPLACE FUNCTION sp_domicilio_cambiar(p_usuario uuid, p_latitud double precision, p_longitud double precision,
                                                p_direccion text)
RETURNS jsonb
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
DECLARE
    v_actual     domicilios := fn_vecino(p_usuario, false);
    v_dias       integer := fn_parametro_entero('domicilio_cambio_dias', 30);
    v_ultimo     timestamptz;
    v_punto      geometry := fn_punto(p_latitud, p_longitud);
    v_cuadrante  cuadrantes%ROWTYPE;
    v_referencia text;
BEGIN
    PERFORM 1 FROM usuarios WHERE id = p_usuario FOR UPDATE;
    SELECT * INTO v_actual FROM domicilios WHERE usuario_id = p_usuario AND vigente;
    IF EXISTS (SELECT 1 FROM alertas WHERE emisor_id = p_usuario AND tipo = 'PANICO' AND estado IN ('ACTIVA', 'ATENDIDA')) THEN
        PERFORM fn_error(409, 'PANICO_ACTIVO', 'No puedes cambiar tu domicilio mientras tengas una alerta de pánico activa');
    END IF;

    SELECT ultimo_cambio_domicilio_en INTO v_ultimo FROM usuarios WHERE id = p_usuario;
    IF v_ultimo IS NOT NULL AND v_ultimo + make_interval(days => v_dias) > clock_timestamp() THEN
        PERFORM fn_error(409, 'CAMBIO_PREMATURO', format('Podrás cambiar tu domicilio a partir del %s',
            to_char((v_ultimo + make_interval(days => v_dias)) AT TIME ZONE 'America/Lima', 'DD/MM/YYYY')));
    END IF;

    SELECT * INTO v_cuadrante FROM cuadrantes WHERE id = fn_cuadrante_de_punto(v_punto);
    IF NOT FOUND THEN
        PERFORM fn_error(409, 'SIN_COBERTURA', 'Tu zona aún no tiene cobertura. Te avisaremos cuando esté disponible');
    END IF;
    v_referencia := fn_referencia_cuadra(p_direccion, v_cuadrante.nombre);

    UPDATE domicilios SET vigente = false, actualizado_en = clock_timestamp() WHERE id = v_actual.id;
    INSERT INTO domicilios (usuario_id, punto, direccion, referencia_cuadra, cuadrante_id)
    VALUES (p_usuario, v_punto, p_direccion, v_referencia, v_cuadrante.id);
    UPDATE usuarios SET ultimo_cambio_domicilio_en = clock_timestamp(), actualizado_en = clock_timestamp()
    WHERE id = p_usuario;

    RETURN jsonb_build_object(
        'cuadranteId', v_cuadrante.id,
        'codigo', v_cuadrante.codigo,
        'nombre', v_cuadrante.nombre,
        'direccion', p_direccion,
        'referenciaCuadra', v_referencia,
        'mensajePrivacidad', 'Solo vecinos verificados de tu cuadrante verán tu zona');
END
$$;

CREATE OR REPLACE FUNCTION fn_cuadrante_propio(p_usuario uuid)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
DECLARE
    v_domicilio domicilios := fn_vecino(p_usuario, true);
BEGIN
    RETURN (
        SELECT jsonb_build_object(
            'cuadranteId', c.id,
            'codigo', c.codigo,
            'nombre', c.nombre,
            'distrito', d.nombre,
            'poligono', ST_AsGeoJSON(c.poligono)::jsonb)
        FROM cuadrantes c JOIN distritos d ON d.id = c.distrito_id
        WHERE c.id = v_domicilio.cuadrante_id);
END
$$;

CREATE OR REPLACE FUNCTION fn_cuadrantes_cercanos(p_usuario uuid)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
DECLARE
    v_domicilio domicilios := fn_vecino(p_usuario, true);
    v_metros    integer := fn_parametro_entero('cuadrantes_cercanos_metros', 3000);
BEGIN
    RETURN coalesce((
        SELECT jsonb_agg(jsonb_build_object('nombre', c.nombre, 'poligono', ST_AsGeoJSON(c.poligono)::jsonb)
                         ORDER BY c.codigo)
        FROM cuadrantes c, cuadrantes propio
        WHERE propio.id = v_domicilio.cuadrante_id
          AND c.id <> propio.id AND c.habilitado AND c.activo
          AND ST_DWithin(c.poligono::geography, propio.poligono::geography, v_metros)), '[]'::jsonb);
END
$$;

CREATE OR REPLACE FUNCTION fn_vecinos_cuadrante(p_usuario uuid)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
DECLARE
    v_domicilio domicilios := fn_vecino(p_usuario, true);
BEGIN
    RETURN coalesce((
        SELECT jsonb_agg(jsonb_build_object(
                   'nombre', fn_nombre_abreviado(u.nombres, u.apellido_paterno),
                   'referenciaCuadra', d.referencia_cuadra,
                   'insignia', CASE WHEN u.sexo = 'F' THEN 'Vecina verificada' ELSE 'Vecino verificado' END)
               ORDER BY u.nombres, u.apellido_paterno)
        FROM domicilios d
        JOIN usuarios u ON u.id = d.usuario_id
        WHERE d.vigente AND d.cuadrante_id = v_domicilio.cuadrante_id
          AND u.activo AND u.tipo = 'VECINO' AND u.id <> p_usuario), '[]'::jsonb);
END
$$;

CREATE OR REPLACE FUNCTION sp_dispositivo_registrar(p_usuario uuid, p_sesion uuid, p_token text)
RETURNS jsonb
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
BEGIN
    IF p_token IS NULL OR btrim(p_token) = '' THEN
        PERFORM fn_error(400, 'TOKEN_PUSH_INVALIDO', 'El identificador de notificaciones no es válido');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM sesiones WHERE id = p_sesion AND usuario_id = p_usuario AND revocada_en IS NULL) THEN
        PERFORM fn_error(401, 'SESION_INVALIDA', 'Tu sesión no es válida. Inicia sesión nuevamente');
    END IF;

    UPDATE dispositivos SET valido = false, actualizado_en = clock_timestamp()
    WHERE valido AND token_push = p_token AND sesion_id <> p_sesion;

    UPDATE dispositivos SET token_push = p_token, actualizado_en = clock_timestamp()
    WHERE valido AND sesion_id = p_sesion;
    IF NOT FOUND THEN
        INSERT INTO dispositivos (sesion_id, usuario_id, token_push) VALUES (p_sesion, p_usuario, p_token);
    END IF;
    RETURN jsonb_build_object('registrado', true);
END
$$;

CREATE OR REPLACE FUNCTION sp_dispositivo_invalidar(p_token text)
RETURNS jsonb
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
DECLARE
    v_total integer;
BEGIN
    UPDATE dispositivos SET valido = false, actualizado_en = clock_timestamp()
    WHERE valido AND token_push = p_token;
    GET DIAGNOSTICS v_total = ROW_COUNT;
    RETURN jsonb_build_object('invalidados', v_total);
END
$$;

CREATE OR REPLACE FUNCTION fn_perfil(p_usuario uuid)
RETURNS jsonb
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
    SELECT jsonb_build_object(
        'tipo', u.tipo,
        'dni', fn_dni_enmascarado(u.dni),
        'nombres', u.nombres,
        'apellidoPaterno', u.apellido_paterno,
        'apellidoMaterno', u.apellido_materno,
        'edad', fn_edad(u.fecha_nacimiento),
        'sexo', u.sexo,
        'celular', u.celular,
        'cuadrante', CASE WHEN c.id IS NULL THEN NULL
                          ELSE jsonb_build_object('cuadranteId', c.id, 'codigo', c.codigo, 'nombre', c.nombre) END,
        'referenciaCuadra', d.referencia_cuadra,
        'verificaciones', jsonb_build_object(
            'dni', true,
            'celular', true,
            'domicilio', u.tipo <> 'VECINO' OR d.id IS NOT NULL),
        'sinCobertura', u.tipo = 'VECINO' AND d.cuadrante_id IS NULL,
        'insignia', CASE WHEN u.tipo <> 'VECINO' THEN NULL
                         WHEN u.sexo = 'F' THEN 'Vecina verificada' ELSE 'Vecino verificado' END)
    FROM usuarios u
    LEFT JOIN domicilios d ON d.usuario_id = u.id AND d.vigente
    LEFT JOIN cuadrantes c ON c.id = d.cuadrante_id
    WHERE u.id = p_usuario AND u.activo;
$$;

CREATE OR REPLACE FUNCTION fn_telefonos_emergencia(p_usuario uuid)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
DECLARE
    v_distrito distritos%ROWTYPE;
    v_lista    jsonb;
BEGIN
    SELECT di.* INTO v_distrito
    FROM usuarios u
    LEFT JOIN domicilios d ON d.usuario_id = u.id AND d.vigente
    LEFT JOIN cuadrantes c ON c.id = d.cuadrante_id
    LEFT JOIN cuentas_serenazgo cs ON cs.usuario_id = u.id AND cs.activo
    JOIN distritos di ON di.id = coalesce(cs.distrito_id, c.distrito_id)
    WHERE u.id = p_usuario;

    IF FOUND THEN
        SELECT jsonb_agg(t ORDER BY orden) INTO v_lista FROM (
            SELECT 1 AS orden, jsonb_build_object('servicio', 'Serenazgo', 'numero', v_distrito.telefono_serenazgo) AS t
            WHERE v_distrito.telefono_serenazgo IS NOT NULL
            UNION ALL
            SELECT 2, jsonb_build_object('servicio', 'Comisaría', 'numero', v_distrito.telefono_comisaria)
            WHERE v_distrito.telefono_comisaria IS NOT NULL
            UNION ALL
            SELECT 3, jsonb_build_object('servicio', 'Bomberos', 'numero', v_distrito.telefono_bomberos)
            WHERE v_distrito.telefono_bomberos IS NOT NULL) x;
        IF v_lista IS NOT NULL THEN
            RETURN jsonb_build_object('fuente', 'DISTRITO', 'distrito', v_distrito.nombre, 'telefonos', v_lista);
        END IF;
    END IF;

    RETURN jsonb_build_object('fuente', 'NACIONAL', 'distrito', v_distrito.nombre, 'telefonos', coalesce((
        SELECT jsonb_agg(jsonb_build_object('servicio', servicio, 'numero', numero) ORDER BY orden)
        FROM telefonos_nacionales WHERE activo), '[]'::jsonb));
END
$$;

CREATE OR REPLACE FUNCTION sp_reporte_datos_civiles(p_usuario uuid, p_descripcion text)
RETURNS jsonb
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
DECLARE
    v_id uuid;
BEGIN
    IF p_descripcion IS NULL OR btrim(p_descripcion) = '' THEN
        PERFORM fn_error(400, 'DESCRIPCION_REQUERIDA', 'Cuéntanos qué dato es incorrecto');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM usuarios WHERE id = p_usuario AND activo) THEN
        PERFORM fn_error(401, 'SESION_INVALIDA', 'Tu sesión no es válida. Inicia sesión nuevamente');
    END IF;
    INSERT INTO reportes_datos_civiles (usuario_id, descripcion) VALUES (p_usuario, btrim(p_descripcion))
    RETURNING id INTO v_id;
    INSERT INTO avisos_administrador (tipo, usuario_id, referencia_id, detalle)
    VALUES ('DATOS_CIVILES', p_usuario, v_id, 'Un vecino reportó datos civiles incorrectos');
    RETURN jsonb_build_object(
        'recibido', true,
        'mensaje', 'Recibimos tu reporte. La corrección de tus datos se realiza ante RENIEC; se actualizarán cuando vuelvas a validar tu DNI');
END
$$;
