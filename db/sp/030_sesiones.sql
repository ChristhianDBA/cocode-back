CREATE OR REPLACE FUNCTION fn_hash(p_texto text)
RETURNS text
LANGUAGE sql
IMMUTABLE
SET search_path = public, extensions, pg_temp
AS $$
    SELECT encode(digest(p_texto, 'sha256'), 'hex');
$$;

CREATE OR REPLACE FUNCTION sp_sesion_crear(p_usuario uuid)
RETURNS jsonb
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
DECLARE
    v_token text := encode(gen_random_bytes(32), 'hex');
    v_id    uuid;
BEGIN
    INSERT INTO sesiones (usuario_id, token_renovacion_hash)
    VALUES (p_usuario, fn_hash(v_token))
    RETURNING id INTO v_id;
    RETURN jsonb_build_object('sesionId', v_id, 'tokenRenovacion', v_token);
END
$$;

CREATE OR REPLACE FUNCTION fn_validar_sesion(p_sesion uuid)
RETURNS jsonb
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
    SELECT jsonb_build_object(
        'usuarioId', u.id,
        'sesionId', s.id,
        'tipo', u.tipo,
        'nombres', u.nombres,
        'sexo', u.sexo,
        'cuadranteId', d.cuadrante_id,
        'distritoId', coalesce(cs.distrito_id, c.distrito_id),
        'roles', to_jsonb(array_remove(ARRAY[
            u.tipo,
            CASE WHEN EXISTS (SELECT 1 FROM roles_coordinador r WHERE r.usuario_id = u.id AND r.activo)
                 THEN 'COORDINADOR' END], NULL)))
    FROM sesiones s
    JOIN usuarios u ON u.id = s.usuario_id AND u.activo
    LEFT JOIN domicilios d ON d.usuario_id = u.id AND d.vigente
    LEFT JOIN cuadrantes c ON c.id = d.cuadrante_id
    LEFT JOIN cuentas_serenazgo cs ON cs.usuario_id = u.id AND cs.activo
    WHERE s.id = p_sesion AND s.revocada_en IS NULL AND s.activo
      AND (u.tipo <> 'SERENAZGO' OR cs.id IS NOT NULL);
$$;

CREATE OR REPLACE FUNCTION sp_sesion_renovar(p_token text)
RETURNS jsonb
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
DECLARE
    v_sesion uuid;
    v_token  text := encode(gen_random_bytes(32), 'hex');
BEGIN
    SELECT s.id INTO v_sesion
    FROM sesiones s
    JOIN usuarios u ON u.id = s.usuario_id AND u.activo
    WHERE s.token_renovacion_hash = fn_hash(coalesce(p_token, '')) AND s.revocada_en IS NULL AND s.activo
    FOR UPDATE OF s;

    IF v_sesion IS NULL THEN
        PERFORM fn_error(401, 'SESION_INVALIDA', 'Tu sesión no es válida. Inicia sesión nuevamente');
    END IF;

    UPDATE sesiones SET token_renovacion_hash = fn_hash(v_token), renovada_en = clock_timestamp()
    WHERE id = v_sesion;
    RETURN jsonb_build_object('sesionId', v_sesion, 'tokenRenovacion', v_token);
END
$$;

CREATE OR REPLACE FUNCTION sp_sesiones_revocar(p_usuario uuid, p_sesion uuid, p_motivo text)
RETURNS integer
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
DECLARE
    v_total integer;
BEGIN
    IF p_usuario IS NULL AND p_sesion IS NULL THEN
        RAISE EXCEPTION 'sp_sesiones_revocar requiere usuario o sesión';
    END IF;
    UPDATE dispositivos SET valido = false, actualizado_en = clock_timestamp()
    WHERE valido AND sesion_id IN (
        SELECT id FROM sesiones
        WHERE revocada_en IS NULL AND (p_sesion IS NULL OR id = p_sesion) AND (p_usuario IS NULL OR usuario_id = p_usuario));

    UPDATE sesiones SET revocada_en = clock_timestamp(), motivo_revocacion = p_motivo
    WHERE revocada_en IS NULL AND (p_sesion IS NULL OR id = p_sesion) AND (p_usuario IS NULL OR usuario_id = p_usuario);
    GET DIAGNOSTICS v_total = ROW_COUNT;
    RETURN v_total;
END
$$;

CREATE OR REPLACE FUNCTION sp_sesion_cerrar(p_sesion uuid)
RETURNS jsonb
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
BEGIN
    IF p_sesion IS NULL THEN
        PERFORM fn_error(400, 'SESION_INVALIDA', 'Tu sesión no es válida. Inicia sesión nuevamente');
    END IF;
    PERFORM sp_sesiones_revocar(NULL, p_sesion, 'CIERRE');
    RETURN jsonb_build_object('cerrada', true);
END
$$;
