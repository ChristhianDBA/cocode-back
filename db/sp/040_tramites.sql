CREATE OR REPLACE FUNCTION fn_edad(p_fecha_nacimiento date)
RETURNS integer
LANGUAGE sql
STABLE
AS $$
    SELECT date_part('year', age(current_date, p_fecha_nacimiento))::integer;
$$;

CREATE OR REPLACE FUNCTION fn_tramite(p_token text)
RETURNS altas_en_curso
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_fila altas_en_curso%ROWTYPE;
BEGIN
    SELECT * INTO v_fila FROM altas_en_curso
    WHERE token_hash = fn_hash(coalesce(p_token, '')) AND activo AND completada_en IS NULL;
    IF NOT FOUND THEN
        PERFORM fn_error(401, 'TRAMITE_INVALIDO', 'Tu registro no es válido. Vuelve a empezar');
    END IF;
    IF v_fila.vence_en < clock_timestamp() THEN
        PERFORM fn_error(410, 'TRAMITE_VENCIDO', 'Tu registro venció. Vuelve a empezar');
    END IF;
    RETURN v_fila;
END
$$;

CREATE OR REPLACE FUNCTION sp_tramite_iniciar(p_proposito text, p_dni text, p_nombres text, p_apellido_paterno text,
                                              p_apellido_materno text, p_fecha_nacimiento date, p_sexo text, p_origen text)
RETURNS jsonb
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_edad   integer;
    v_token  text := encode(gen_random_bytes(32), 'hex');
    v_vence  timestamptz := clock_timestamp() + make_interval(hours => fn_parametro_entero('alta_vigencia_horas', 24));
BEGIN
    IF p_dni IS NULL OR p_dni !~ '^\d{8}$' THEN
        PERFORM fn_error(400, 'DNI_INVALIDO', 'El DNI debe tener 8 dígitos');
    END IF;
    IF p_origen IS NULL OR p_origen NOT IN ('ESCANEO', 'MANUAL') THEN
        PERFORM fn_error(400, 'ORIGEN_INVALIDO', 'El origen debe ser ESCANEO o MANUAL');
    END IF;
    IF p_proposito NOT IN ('ALTA', 'RECUPERACION') THEN
        PERFORM fn_error(400, 'PROPOSITO_INVALIDO', 'Trámite no válido');
    END IF;

    v_edad := fn_edad(p_fecha_nacimiento);
    IF p_proposito = 'ALTA' THEN
        IF EXISTS (SELECT 1 FROM usuarios WHERE dni = p_dni) THEN
            PERFORM fn_error(409, 'DNI_REGISTRADO', 'Este DNI ya está registrado. Inicia sesión');
        END IF;
        IF v_edad < fn_parametro_entero('edad_minima', 18) THEN
            PERFORM fn_error(409, 'MENOR_DE_EDAD', 'Debes ser mayor de edad para unirte a la red vecinal');
        END IF;
    END IF;


    INSERT INTO altas_en_curso (token_hash, proposito, dni, nombres, apellido_paterno, apellido_materno,
                                fecha_nacimiento, sexo, origen, vence_en)
    VALUES (fn_hash(v_token), p_proposito, p_dni, p_nombres, p_apellido_paterno, p_apellido_materno,
            p_fecha_nacimiento, p_sexo, p_origen, v_vence);

    RETURN jsonb_build_object(
        'tokenTramite', v_token,
        'venceEn', v_vence,
        'datos', jsonb_build_object(
            'dni', fn_dni_enmascarado(p_dni),
            'nombres', p_nombres,
            'apellidoPaterno', p_apellido_paterno,
            'apellidoMaterno', p_apellido_materno,
            'edad', v_edad,
            'sexo', p_sexo,
            'verificado', true,
            'editable', false));
END
$$;

CREATE OR REPLACE FUNCTION sp_tramite_solicitar_codigo(p_token text, p_celular text, p_canal text)
RETURNS jsonb
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_tramite altas_en_curso := fn_tramite(p_token);
BEGIN
    IF p_celular IS NULL OR p_celular !~ '^9\d{8}$' THEN
        PERFORM fn_error(400, 'CELULAR_INVALIDO', 'El celular debe tener 9 dígitos y empezar con 9');
    END IF;
    IF EXISTS (SELECT 1 FROM usuarios WHERE celular = p_celular AND dni <> v_tramite.dni) THEN
        PERFORM fn_error(409, 'CELULAR_REGISTRADO', 'Este celular ya está registrado en otra cuenta');
    END IF;

    UPDATE altas_en_curso
    SET celular = p_celular, celular_verificado_en = NULL, actualizado_en = clock_timestamp()
    WHERE id = v_tramite.id;

    RETURN sp_codigo_emitir(v_tramite.proposito, v_tramite.id, p_celular, p_canal);
END
$$;

CREATE OR REPLACE FUNCTION sp_tramite_verificar_codigo(p_token text, p_codigo text)
RETURNS jsonb
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_tramite   altas_en_curso := fn_tramite(p_token);
    v_resultado jsonb;
BEGIN
    v_resultado := fn_codigo_verificar(NULL, v_tramite.proposito, v_tramite.id, p_codigo);
    IF v_resultado ? 'error' THEN
        RETURN v_resultado;
    END IF;
    UPDATE altas_en_curso
    SET celular = v_resultado->>'celular', celular_verificado_en = clock_timestamp(), actualizado_en = clock_timestamp()
    WHERE id = v_tramite.id;
    RETURN jsonb_build_object('celularVerificado', true);
END
$$;

CREATE OR REPLACE FUNCTION sp_alta_guardar_domicilio(p_token text, p_latitud double precision,
                                                     p_longitud double precision, p_direccion text)
RETURNS jsonb
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_tramite    altas_en_curso := fn_tramite(p_token);
    v_punto      geometry := fn_punto(p_latitud, p_longitud);
    v_cuadrante  cuadrantes%ROWTYPE;
    v_referencia text;
BEGIN
    IF v_tramite.proposito <> 'ALTA' THEN
        PERFORM fn_error(409, 'TRAMITE_INVALIDO', 'Tu registro no es válido. Vuelve a empezar');
    END IF;
    SELECT * INTO v_cuadrante FROM cuadrantes WHERE id = fn_cuadrante_de_punto(v_punto);
    IF NOT FOUND THEN
        PERFORM fn_error(409, 'SIN_COBERTURA', 'Tu zona aún no tiene cobertura. Te avisaremos cuando esté disponible');
    END IF;
    v_referencia := fn_referencia_cuadra(p_direccion, v_cuadrante.nombre);

    UPDATE altas_en_curso
    SET punto = v_punto, direccion = p_direccion, referencia_cuadra = v_referencia, cuadrante_id = v_cuadrante.id,
        domicilio_verificado_en = clock_timestamp(), actualizado_en = clock_timestamp()
    WHERE id = v_tramite.id;

    RETURN jsonb_build_object(
        'cuadranteId', v_cuadrante.id,
        'codigo', v_cuadrante.codigo,
        'nombre', v_cuadrante.nombre,
        'direccion', p_direccion,
        'referenciaCuadra', v_referencia,
        'mensajePrivacidad', 'Solo vecinos verificados de tu cuadrante verán tu zona');
END
$$;

CREATE OR REPLACE FUNCTION fn_tramite_estado(p_token text)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_t      altas_en_curso := fn_tramite(p_token);
    v_faltan text[] := ARRAY[]::text[];
BEGIN
    IF v_t.celular_verificado_en IS NULL THEN
        v_faltan := array_append(v_faltan, 'CELULAR');
    END IF;
    IF v_t.proposito = 'ALTA' AND v_t.domicilio_verificado_en IS NULL THEN
        v_faltan := array_append(v_faltan, 'DOMICILIO');
    END IF;
    RETURN jsonb_build_object(
        'proposito', v_t.proposito,
        'venceEn', v_t.vence_en,
        'datos', jsonb_build_object('dni', fn_dni_enmascarado(v_t.dni), 'nombres', v_t.nombres, 'apellidoPaterno', v_t.apellido_paterno,
                                    'apellidoMaterno', v_t.apellido_materno, 'edad', fn_edad(v_t.fecha_nacimiento),
                                    'sexo', v_t.sexo, 'verificado', true, 'editable', false),
        'celularVerificado', v_t.celular_verificado_en IS NOT NULL,
        'domicilioGuardado', v_t.domicilio_verificado_en IS NOT NULL,
        'referenciaCuadra', v_t.referencia_cuadra,
        'faltan', to_jsonb(v_faltan),
        'puedeRegistrar', cardinality(v_faltan) = 0);
END
$$;

CREATE OR REPLACE FUNCTION sp_alta_completar(p_token text, p_acepta_tratamiento boolean)
RETURNS jsonb
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_t        altas_en_curso := fn_tramite(p_token);
    v_usuario  uuid;
    v_sesion   jsonb;
    v_restriccion text;
BEGIN
    IF v_t.proposito <> 'ALTA' THEN
        PERFORM fn_error(409, 'TRAMITE_INVALIDO', 'Tu registro no es válido. Vuelve a empezar');
    END IF;
    IF v_t.celular_verificado_en IS NULL THEN
        PERFORM fn_error(409, 'FALTA_CELULAR', 'Falta verificar tu celular');
    END IF;
    IF v_t.domicilio_verificado_en IS NULL THEN
        PERFORM fn_error(409, 'FALTA_DOMICILIO', 'Falta guardar tu domicilio en el mapa');
    END IF;
    IF p_acepta_tratamiento IS DISTINCT FROM true THEN
        PERFORM fn_error(409, 'FALTA_ACEPTACION', 'Debes aceptar el tratamiento de tus datos personales para registrarte');
    END IF;
    IF fn_edad(v_t.fecha_nacimiento) < fn_parametro_entero('edad_minima', 18) THEN
        PERFORM fn_error(409, 'MENOR_DE_EDAD', 'Debes ser mayor de edad para unirte a la red vecinal');
    END IF;
    IF fn_cuadrante_de_punto(v_t.punto) IS NULL THEN
        PERFORM fn_error(409, 'SIN_COBERTURA', 'Tu zona aún no tiene cobertura. Te avisaremos cuando esté disponible');
    END IF;

    BEGIN
        INSERT INTO usuarios (dni, nombres, apellido_paterno, apellido_materno, fecha_nacimiento, sexo, celular, tipo,
                              acepta_tratamiento_en, ultimo_cambio_domicilio_en)
        VALUES (v_t.dni, v_t.nombres, v_t.apellido_paterno, v_t.apellido_materno, v_t.fecha_nacimiento, v_t.sexo,
                v_t.celular, 'VECINO', clock_timestamp(), NULL)
        RETURNING id INTO v_usuario;
    EXCEPTION WHEN unique_violation THEN
        GET STACKED DIAGNOSTICS v_restriccion = CONSTRAINT_NAME;
        IF v_restriccion = 'usuarios_celular_key' THEN
            PERFORM fn_error(409, 'CELULAR_REGISTRADO', 'Este celular ya está registrado en otra cuenta');
        END IF;
        PERFORM fn_error(409, 'DNI_REGISTRADO', 'Este DNI ya está registrado. Inicia sesión');
    END;

    INSERT INTO domicilios (usuario_id, punto, direccion, referencia_cuadra, cuadrante_id)
    VALUES (v_usuario, v_t.punto, v_t.direccion, v_t.referencia_cuadra, fn_cuadrante_de_punto(v_t.punto));

    UPDATE altas_en_curso SET completada_en = clock_timestamp(), usuario_id = v_usuario, actualizado_en = clock_timestamp()
    WHERE id = v_t.id;

    v_sesion := sp_sesion_crear(v_usuario);
    RETURN v_sesion || jsonb_build_object(
        'usuarioId', v_usuario,
        'mensajeBienvenida', CASE WHEN v_t.sexo = 'F' THEN '¡Bienvenida al barrio!' ELSE '¡Bienvenido al barrio!' END,
        'insignia', CASE WHEN v_t.sexo = 'F' THEN 'Vecina verificada' ELSE 'Vecino verificado' END);
END
$$;
