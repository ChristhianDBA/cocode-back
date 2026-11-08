CREATE OR REPLACE FUNCTION fn_celular_enmascarado(p_celular text)
RETURNS text
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT '******' || right(p_celular, 3);
$$;

CREATE OR REPLACE FUNCTION sp_ingreso_solicitar(p_dni text, p_canal text)
RETURNS jsonb
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_usuario usuarios%ROWTYPE;
    v_envio   jsonb;
BEGIN
    IF p_dni IS NULL OR p_dni !~ '^\d{8}$' THEN
        PERFORM fn_error(400, 'DNI_INVALIDO', 'El DNI debe tener 8 dígitos');
    END IF;
    IF p_canal IS NULL OR p_canal NOT IN ('SMS', 'WHATSAPP') THEN
        PERFORM fn_error(400, 'CANAL_INVALIDO', 'Elige recibir el código por SMS o WhatsApp');
    END IF;

    SELECT * INTO v_usuario FROM usuarios WHERE dni = p_dni AND activo;
    IF NOT FOUND THEN
        RETURN jsonb_build_object(
            'tokenIngreso', gen_random_uuid(),
            'celularEnmascarado', '******' || lpad((abs(hashtext('ingreso:' || p_dni)) % 1000)::text, 3, '0'),
            'venceEnSegundos', fn_parametro_entero('codigo_vigencia_minutos', 5) * 60,
            'reenvioEnSegundos', fn_parametro_entero('codigo_reenvio_segundos', 30));
    END IF;

    v_envio := sp_codigo_emitir('INGRESO', v_usuario.id, v_usuario.celular, p_canal);
    RETURN jsonb_build_object(
        'tokenIngreso', v_envio->'codigoId',
        'celularEnmascarado', fn_celular_enmascarado(v_usuario.celular),
        'venceEnSegundos', v_envio->'venceEnSegundos',
        'reenvioEnSegundos', v_envio->'reenvioEnSegundos',
        'envio', jsonb_build_object('celular', v_usuario.celular, 'codigo', v_envio->>'codigo', 'canal', p_canal,
                                    'minutosDeVigencia', v_envio->'minutosDeVigencia'));
END
$$;

CREATE OR REPLACE FUNCTION sp_ingreso_verificar(p_token_ingreso text, p_codigo text)
RETURNS jsonb
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_id        uuid;
    v_resultado jsonb;
    v_usuario   uuid;
BEGIN
    BEGIN
        v_id := p_token_ingreso::uuid;
    EXCEPTION WHEN invalid_text_representation THEN
        v_id := NULL;
    END;
    IF v_id IS NULL THEN
        RETURN jsonb_build_object('error', jsonb_build_object(
            'estado', 400, 'codigo', 'CODIGO_INVALIDO', 'mensaje', 'El código no es válido o ya venció'));
    END IF;

    v_resultado := fn_codigo_verificar(v_id, 'INGRESO', NULL, p_codigo);
    IF v_resultado ? 'error' THEN
        RETURN v_resultado;
    END IF;

    SELECT id INTO v_usuario FROM usuarios
    WHERE id = (v_resultado->>'referencia')::uuid AND activo AND celular = v_resultado->>'celular';
    IF v_usuario IS NULL THEN
        RETURN jsonb_build_object('error', jsonb_build_object(
            'estado', 400, 'codigo', 'CODIGO_INVALIDO', 'mensaje', 'El código no es válido o ya venció'));
    END IF;
    RETURN sp_sesion_crear(v_usuario);
END
$$;
