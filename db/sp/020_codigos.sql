CREATE OR REPLACE FUNCTION fn_generar_codigo()
RETURNS text
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
DECLARE
    v_fijo   text := fn_parametro('codigo_fijo', NULL);
    v_bytes  bytea;
    v_numero integer;
BEGIN
    IF v_fijo IS NOT NULL THEN
        RETURN v_fijo;
    END IF;
    LOOP
        v_bytes := gen_random_bytes(2);
        v_numero := (get_byte(v_bytes, 0) << 8) | get_byte(v_bytes, 1);
        IF v_numero < 60000 THEN
            RETURN lpad((v_numero % 10000)::text, 4, '0');
        END IF;
    END LOOP;
END
$$;

CREATE OR REPLACE FUNCTION sp_codigo_emitir(p_proposito text, p_referencia uuid, p_celular text, p_canal text)
RETURNS jsonb
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
DECLARE
    v_reenvio   integer := fn_parametro_entero('codigo_reenvio_segundos', 30);
    v_vigencia  integer := fn_parametro_entero('codigo_vigencia_minutos', 5);
    v_ultimo    timestamptz;
    v_faltan    integer;
    v_codigo    text;
    v_id        uuid;
BEGIN
    IF p_canal IS NULL OR p_canal NOT IN ('SMS', 'WHATSAPP') THEN
        PERFORM fn_error(400, 'CANAL_INVALIDO', 'Elige recibir el código por SMS o WhatsApp');
    END IF;

    PERFORM pg_advisory_xact_lock(hashtext('codigo:' || p_proposito || ':' || p_celular));

    IF (SELECT count(*) FROM codigos_verificacion
        WHERE proposito = p_proposito AND (referencia = p_referencia OR celular = p_celular)
          AND enviado_en > clock_timestamp() - interval '1 hour') >= fn_parametro_entero('codigos_maximos_hora', 5) THEN
        PERFORM fn_error(429, 'DEMASIADOS_CODIGOS', 'Solicitaste demasiados códigos. Intenta nuevamente en una hora');
    END IF;

    SELECT max(enviado_en) INTO v_ultimo
    FROM codigos_verificacion
    WHERE proposito = p_proposito AND (referencia = p_referencia OR celular = p_celular);

    IF v_ultimo IS NOT NULL AND v_ultimo > clock_timestamp() - make_interval(secs => v_reenvio) THEN
        v_faltan := greatest(1, ceil(extract(epoch FROM (v_ultimo + make_interval(secs => v_reenvio) - clock_timestamp())))::integer);
        PERFORM fn_error(429, 'REENVIO_PREMATURO', format('Podrás pedir un nuevo código en %s segundos', v_faltan));
    END IF;

    UPDATE codigos_verificacion SET activo = false
    WHERE referencia = p_referencia AND proposito = p_proposito AND activo;

    v_codigo := fn_generar_codigo();
    INSERT INTO codigos_verificacion (celular, proposito, referencia, canal, codigo_hash, enviado_en, vence_en)
    VALUES (p_celular, p_proposito, p_referencia, p_canal, crypt(v_codigo, gen_salt('bf', 6)),
            clock_timestamp(), clock_timestamp() + make_interval(mins => v_vigencia))
    RETURNING id INTO v_id;

    RETURN jsonb_build_object(
        'codigoId', v_id,
        'codigo', v_codigo,
        'celular', p_celular,
        'canal', p_canal,
        'minutosDeVigencia', v_vigencia,
        'venceEnSegundos', v_vigencia * 60,
        'reenvioEnSegundos', v_reenvio);
END
$$;

CREATE OR REPLACE FUNCTION fn_codigo_verificar(p_codigo_id uuid, p_proposito text, p_referencia uuid, p_codigo text)
RETURNS jsonb
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
DECLARE
    v_maximos integer := fn_parametro_entero('codigo_intentos_maximos', 5);
    v_fila    codigos_verificacion%ROWTYPE;
    v_error   jsonb := jsonb_build_object('error', jsonb_build_object(
                  'estado', 400, 'codigo', 'CODIGO_INVALIDO', 'mensaje', 'El código no es válido o ya venció'));
BEGIN
    IF p_codigo_id IS NOT NULL THEN
        SELECT * INTO v_fila FROM codigos_verificacion
        WHERE id = p_codigo_id AND proposito = p_proposito
        FOR UPDATE;
    ELSE
        SELECT * INTO v_fila FROM codigos_verificacion
        WHERE referencia = p_referencia AND proposito = p_proposito AND activo
        ORDER BY enviado_en DESC
        LIMIT 1
        FOR UPDATE;
    END IF;

    IF NOT FOUND OR NOT v_fila.activo OR v_fila.usado_en IS NOT NULL OR v_fila.vence_en < clock_timestamp() THEN
        RETURN v_error;
    END IF;

    IF p_codigo IS NULL OR crypt(p_codigo, v_fila.codigo_hash) <> v_fila.codigo_hash THEN
        UPDATE codigos_verificacion
        SET intentos = intentos + 1,
            activo = (intentos + 1) < v_maximos
        WHERE id = v_fila.id;
        IF v_fila.intentos + 1 >= v_maximos THEN
            RETURN jsonb_build_object('error', jsonb_build_object(
                'estado', 400, 'codigo', 'CODIGO_BLOQUEADO',
                'mensaje', 'Superaste los intentos permitidos. Solicita un nuevo código'));
        END IF;
        RETURN v_error;
    END IF;

    UPDATE codigos_verificacion SET usado_en = clock_timestamp(), activo = false WHERE id = v_fila.id;
    RETURN jsonb_build_object('ok', true, 'celular', v_fila.celular, 'referencia', v_fila.referencia);
END
$$;

CREATE OR REPLACE FUNCTION sp_codigo_anular(p_codigo_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
BEGIN
    UPDATE codigos_verificacion
    SET activo = false, enviado_en = enviado_en - interval '1 day'
    WHERE id = p_codigo_id AND usado_en IS NULL;
    RETURN jsonb_build_object('anulado', FOUND);
END
$$;

CREATE OR REPLACE FUNCTION fn_codigo_fijo_activo()
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
    SELECT EXISTS (SELECT 1 FROM parametros WHERE clave = 'codigo_fijo' AND activo);
$$;
