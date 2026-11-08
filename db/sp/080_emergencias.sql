CREATE OR REPLACE FUNCTION fn_alerta_json(p_alerta uuid, p_con_ubicacion boolean)
RETURNS jsonb
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
    SELECT jsonb_build_object(
        'alertaId', a.id,
        'tipo', a.tipo,
        'estado', a.estado,
        'emisor', fn_nombre_abreviado(u.nombres, u.apellido_paterno),
        'referenciaCuadra', a.referencia_cuadra,
        'ubicacionAproximada', a.ubicacion_aproximada,
        'descripcion', a.descripcion,
        'cuadranteId', a.cuadrante_id,
        'creadaEn', a.creada_en,
        'actualizadaEn', a.actualizada_en)
        || CASE WHEN p_con_ubicacion AND NOT a.ubicacion_aproximada
                THEN jsonb_build_object('latitud', ST_Y(a.punto), 'longitud', ST_X(a.punto))
                ELSE '{}'::jsonb END
    FROM alertas a JOIN usuarios u ON u.id = a.emisor_id
    WHERE a.id = p_alerta;
$$;

CREATE OR REPLACE FUNCTION fn_distrito_alerta(p_alerta uuid)
RETURNS integer
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
    SELECT coalesce(c1.distrito_id, c2.distrito_id)
    FROM alertas a
    LEFT JOIN cuadrantes c1 ON c1.id = a.cuadrante_id
    LEFT JOIN cuadrantes c2 ON c2.id = a.cuadrante_domicilio_id
    WHERE a.id = p_alerta;
$$;

CREATE OR REPLACE FUNCTION fn_destinatarios(p_alerta uuid)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
DECLARE
    v_a            alertas%ROWTYPE;
    v_distrito     integer := fn_distrito_alerta(p_alerta);
    v_vecinos      uuid[];
    v_coordinadores uuid[];
    v_serenazgo    uuid[];
BEGIN
    SELECT * INTO v_a FROM alertas WHERE id = p_alerta;

    SELECT coalesce(array_agg(DISTINCT d.usuario_id), '{}') INTO v_vecinos
    FROM domicilios d JOIN usuarios u ON u.id = d.usuario_id AND u.activo AND u.tipo = 'VECINO'
    WHERE d.vigente AND d.cuadrante_id = v_a.cuadrante_id AND u.id <> v_a.emisor_id;

    SELECT coalesce(array_agg(DISTINCT r.usuario_id), '{}') INTO v_coordinadores
    FROM roles_coordinador r JOIN usuarios u ON u.id = r.usuario_id AND u.activo
    WHERE r.activo AND r.usuario_id <> v_a.emisor_id
      AND (r.cuadrante_id = v_a.cuadrante_id OR (v_a.tipo = 'PANICO' AND r.cuadrante_id = v_a.cuadrante_domicilio_id));

    SELECT coalesce(array_agg(DISTINCT cs.usuario_id), '{}') INTO v_serenazgo
    FROM cuentas_serenazgo cs JOIN usuarios u ON u.id = cs.usuario_id AND u.activo
    WHERE cs.activo AND cs.distrito_id = v_distrito;

    RETURN jsonb_build_object(
        'cuadrantes', CASE WHEN v_a.cuadrante_id IS NULL THEN '[]'::jsonb ELSE jsonb_build_array(v_a.cuadrante_id) END,
        'coordinadores', to_jsonb(v_coordinadores),
        'serenazgo', to_jsonb(v_serenazgo),
        'dispositivos', coalesce((
            SELECT jsonb_agg(DISTINCT dv.token_push)
            FROM dispositivos dv
            WHERE dv.valido AND dv.usuario_id = ANY (v_vecinos || v_coordinadores || v_serenazgo)), '[]'::jsonb));
END
$$;

CREATE OR REPLACE FUNCTION fn_resultado_alerta(p_alerta uuid, p_extra jsonb)
RETURNS jsonb
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
    SELECT jsonb_build_object(
        'alerta', fn_alerta_json(p_alerta, false),
        'alertaConUbicacion', fn_alerta_json(p_alerta, true),
        'destinatarios', fn_destinatarios(p_alerta)) || coalesce(p_extra, '{}'::jsonb);
$$;

CREATE OR REPLACE FUNCTION sp_panico_emitir(p_usuario uuid, p_confirmado boolean, p_latitud double precision,
                                            p_longitud double precision)
RETURNS jsonb
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
DECLARE
    v_domicilio   domicilios := fn_vecino(p_usuario, false);
    v_existente   uuid;
    v_suspension  suspensiones_panico%ROWTYPE;
    v_punto       geometry;
    v_aproximada  boolean := p_latitud IS NULL OR p_longitud IS NULL;
    v_cuadrante   uuid;
    v_referencia  text;
    v_id          uuid;
BEGIN
    PERFORM 1 FROM usuarios WHERE id = p_usuario FOR UPDATE;
    IF p_confirmado IS DISTINCT FROM true THEN
        PERFORM fn_error(400, 'CONFIRMACION_REQUERIDA', 'Confirma que deseas enviar la alerta de pánico');
    END IF;

    SELECT * INTO v_suspension FROM suspensiones_panico WHERE usuario_id = p_usuario AND levantada_en IS NULL;
    IF FOUND THEN
        PERFORM fn_error(403, 'PANICO_SUSPENDIDO', 'Tu botón de pánico está suspendido: ' || v_suspension.motivo
            || '. Si es una emergencia, llama a los teléfonos de emergencia');
    END IF;

    SELECT id INTO v_existente FROM alertas
    WHERE emisor_id = p_usuario AND tipo = 'PANICO' AND estado IN ('ACTIVA', 'ATENDIDA');
    IF v_existente IS NOT NULL THEN
        RETURN fn_resultado_alerta(v_existente, jsonb_build_object('creada', false, 'fueraDeCobertura', false));
    END IF;

    v_punto := CASE WHEN v_aproximada THEN v_domicilio.punto ELSE fn_punto(p_latitud, p_longitud) END;
    v_cuadrante := fn_cuadrante_de_punto(v_punto);
    IF v_aproximada OR ST_DWithin(v_punto::geography, v_domicilio.punto::geography,
                                  fn_parametro_entero('panico_radio_domicilio_metros', 150)) THEN
        v_referencia := v_domicilio.referencia_cuadra;
    ELSE
        v_referencia := coalesce((SELECT nombre FROM cuadrantes WHERE id = v_cuadrante), 'Fuera de los cuadrantes');
    END IF;

    BEGIN
        INSERT INTO alertas (tipo, emisor_id, cuadrante_id, cuadrante_domicilio_id, punto, ubicacion_aproximada,
                             referencia_cuadra)
        VALUES ('PANICO', p_usuario, v_cuadrante, v_domicilio.cuadrante_id, v_punto, v_aproximada, v_referencia)
        RETURNING id INTO v_id;
    EXCEPTION WHEN unique_violation THEN
        SELECT id INTO v_existente FROM alertas
        WHERE emisor_id = p_usuario AND tipo = 'PANICO' AND estado IN ('ACTIVA', 'ATENDIDA');
        RETURN fn_resultado_alerta(v_existente, jsonb_build_object('creada', false, 'fueraDeCobertura', false));
    END;

    INSERT INTO alertas_historial (alerta_id, estado_anterior, estado_nuevo, autor_id)
    VALUES (v_id, NULL, 'ACTIVA', p_usuario);

    RETURN fn_resultado_alerta(v_id, jsonb_build_object('creada', true, 'fueraDeCobertura', v_cuadrante IS NULL));
END
$$;

CREATE OR REPLACE FUNCTION sp_reporte_emitir(p_usuario uuid, p_tipo text, p_descripcion text)
RETURNS jsonb
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
DECLARE
    v_domicilio domicilios := fn_vecino(p_usuario, true);
    v_maximo    integer := fn_parametro_entero('reportes_maximos_24h', 5);
    v_primero   timestamptz;
    v_total     integer;
    v_id        uuid;
BEGIN
    PERFORM 1 FROM usuarios WHERE id = p_usuario FOR UPDATE;
    IF p_tipo IS NULL OR p_tipo NOT IN ('SOSPECHA', 'ROBO', 'MEDICA', 'ALUMBRADO') THEN
        PERFORM fn_error(400, 'TIPO_INVALIDO', 'Elige el tipo de reporte: SOSPECHA, ROBO, MEDICA o ALUMBRADO');
    END IF;
    IF p_descripcion IS NULL OR btrim(p_descripcion) = '' THEN
        PERFORM fn_error(400, 'DESCRIPCION_REQUERIDA', 'Describe brevemente la situación');
    END IF;

    SELECT count(*), min(creada_en) INTO v_total, v_primero FROM alertas
    WHERE emisor_id = p_usuario AND tipo <> 'PANICO' AND creada_en > clock_timestamp() - interval '24 hours';
    IF v_total >= v_maximo THEN
        PERFORM fn_error(429, 'LIMITE_REPORTES', format('Alcanzaste el límite de %s reportes en 24 horas. Podrás enviar otro a partir de las %s',
            v_maximo, to_char((v_primero + interval '24 hours') AT TIME ZONE 'America/Lima', 'HH24:MI')));
    END IF;

    INSERT INTO alertas (tipo, emisor_id, cuadrante_id, cuadrante_domicilio_id, punto, ubicacion_aproximada,
                         referencia_cuadra, descripcion)
    VALUES (p_tipo, p_usuario, v_domicilio.cuadrante_id, v_domicilio.cuadrante_id, v_domicilio.punto, true,
            v_domicilio.referencia_cuadra, btrim(p_descripcion))
    RETURNING id INTO v_id;
    INSERT INTO alertas_historial (alerta_id, estado_anterior, estado_nuevo, autor_id)
    VALUES (v_id, NULL, 'ACTIVA', p_usuario);

    RETURN fn_resultado_alerta(v_id, jsonb_build_object('creada', true));
END
$$;

CREATE OR REPLACE FUNCTION fn_puede_gestionar(p_usuario uuid, p_alerta uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
    SELECT EXISTS (
        SELECT 1 FROM alertas a JOIN usuarios u ON u.id = p_usuario AND u.activo
        WHERE a.id = p_alerta AND (
            EXISTS (SELECT 1 FROM roles_coordinador r
                    WHERE r.usuario_id = p_usuario AND r.activo
                      AND (r.cuadrante_id = a.cuadrante_id
                           OR (a.tipo = 'PANICO' AND r.cuadrante_id = a.cuadrante_domicilio_id)))
            OR EXISTS (SELECT 1 FROM cuentas_serenazgo cs
                       WHERE cs.usuario_id = p_usuario AND cs.activo AND cs.distrito_id = fn_distrito_alerta(a.id))));
$$;

CREATE OR REPLACE FUNCTION fn_alertas_activas(p_usuario uuid)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
DECLARE
    v_tipo      text;
    v_cuadrante uuid;
BEGIN
    SELECT u.tipo, d.cuadrante_id INTO v_tipo, v_cuadrante
    FROM usuarios u LEFT JOIN domicilios d ON d.usuario_id = u.id AND d.vigente
    WHERE u.id = p_usuario AND u.activo;

    IF v_tipo IS NULL OR v_tipo = 'ADMIN' THEN
        PERFORM fn_error(403, 'SIN_PERMISO', 'No tienes permiso para realizar esta acción');
    END IF;
    IF v_tipo = 'VECINO' AND v_cuadrante IS NULL AND NOT EXISTS (
        SELECT 1 FROM roles_coordinador WHERE usuario_id = p_usuario AND activo) THEN
        PERFORM fn_error(409, 'SIN_COBERTURA', 'Tu zona aún no tiene cobertura');
    END IF;

    RETURN coalesce((
        SELECT jsonb_agg(fn_alerta_json(a.id, v_tipo = 'SERENAZGO') ORDER BY a.creada_en DESC)
        FROM alertas a
        WHERE a.estado IN ('ACTIVA', 'ATENDIDA')
          AND (a.cuadrante_id = v_cuadrante OR fn_puede_gestionar(p_usuario, a.id))), '[]'::jsonb);
END
$$;

CREATE OR REPLACE FUNCTION fn_puede_suscribir(p_usuario uuid, p_cuadrante uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
    SELECT EXISTS (
        SELECT 1 FROM usuarios u
        WHERE u.id = p_usuario AND u.activo AND (
            EXISTS (SELECT 1 FROM domicilios d WHERE d.usuario_id = u.id AND d.vigente AND d.cuadrante_id = p_cuadrante)
            OR EXISTS (SELECT 1 FROM roles_coordinador r WHERE r.usuario_id = u.id AND r.activo AND r.cuadrante_id = p_cuadrante)
            OR EXISTS (SELECT 1 FROM cuentas_serenazgo cs JOIN cuadrantes c ON c.distrito_id = cs.distrito_id
                       WHERE cs.usuario_id = u.id AND cs.activo AND c.id = p_cuadrante)));
$$;

CREATE OR REPLACE FUNCTION sp_alerta_cambiar_estado(p_usuario uuid, p_alerta uuid, p_estado text)
RETURNS jsonb
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
DECLARE
    v_a          alertas%ROWTYPE;
    v_umbral     integer := fn_parametro_entero('falsas_alarmas_umbral', 3);
    v_ventana    integer := fn_parametro_entero('falsas_alarmas_ventana_dias', 30);
    v_falsas     integer;
    v_suspendido boolean := false;
    v_suspension uuid;
BEGIN
    IF p_estado IS NULL OR p_estado NOT IN ('ATENDIDA', 'FALSA_ALARMA', 'RESUELTA') THEN
        PERFORM fn_error(400, 'ESTADO_INVALIDO', 'El estado debe ser ATENDIDA, FALSA_ALARMA o RESUELTA');
    END IF;
    SELECT * INTO v_a FROM alertas WHERE id = p_alerta AND activo FOR UPDATE;
    IF NOT FOUND THEN
        PERFORM fn_error(404, 'ALERTA_NO_ENCONTRADA', 'La alerta no existe');
    END IF;
    IF v_a.emisor_id = p_usuario THEN
        PERFORM fn_error(403, 'PROPIA_ALERTA', 'No puedes cambiar el estado de tu propia alerta');
    END IF;
    IF NOT fn_puede_gestionar(p_usuario, p_alerta) THEN
        PERFORM fn_error(403, 'SIN_PERMISO', 'No tienes permiso para realizar esta acción');
    END IF;
    IF v_a.estado NOT IN ('ACTIVA', 'ATENDIDA') THEN
        PERFORM fn_error(409, 'ALERTA_CERRADA', 'La alerta ya fue cerrada');
    END IF;
    IF v_a.estado = p_estado THEN
        PERFORM fn_error(409, 'ESTADO_SIN_CAMBIO', 'La alerta ya tiene ese estado');
    END IF;

    UPDATE alertas SET estado = p_estado, actualizada_en = clock_timestamp() WHERE id = p_alerta;
    INSERT INTO alertas_historial (alerta_id, estado_anterior, estado_nuevo, autor_id)
    VALUES (p_alerta, v_a.estado, p_estado, p_usuario);

    IF p_estado = 'FALSA_ALARMA' AND v_a.tipo = 'PANICO' THEN
        SELECT count(*) INTO v_falsas
        FROM alertas_historial h JOIN alertas a ON a.id = h.alerta_id
        WHERE a.emisor_id = v_a.emisor_id AND a.tipo = 'PANICO' AND h.estado_nuevo = 'FALSA_ALARMA'
          AND h.fecha > clock_timestamp() - make_interval(days => v_ventana);
        IF v_falsas >= v_umbral AND NOT EXISTS (
            SELECT 1 FROM suspensiones_panico WHERE usuario_id = v_a.emisor_id AND levantada_en IS NULL) THEN
            INSERT INTO suspensiones_panico (usuario_id, motivo)
            VALUES (v_a.emisor_id, format('%s falsas alarmas en %s días', v_falsas, v_ventana))
            RETURNING id INTO v_suspension;
            INSERT INTO avisos_administrador (tipo, usuario_id, referencia_id, detalle)
            VALUES ('SUSPENSION', v_a.emisor_id, v_suspension,
                    format('Botón de pánico suspendido por %s falsas alarmas en %s días', v_falsas, v_ventana));
            v_suspendido := true;
        END IF;
    END IF;

    RETURN fn_resultado_alerta(p_alerta, jsonb_build_object('emisorSuspendido', v_suspendido));
END
$$;
