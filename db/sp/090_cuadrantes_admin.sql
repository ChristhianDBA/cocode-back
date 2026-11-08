CREATE OR REPLACE FUNCTION sp_cuadrante_deshabilitar(p_admin uuid, p_cuadrante uuid)
RETURNS jsonb
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
DECLARE
    v_alertas     jsonb;
    v_reasignados jsonb;
    v_sin         jsonb;
BEGIN
    PERFORM fn_exigir_admin(p_admin);
    UPDATE cuadrantes SET habilitado = false, actualizado_en = clock_timestamp()
    WHERE id = p_cuadrante AND activo AND habilitado;
    IF NOT FOUND THEN
        PERFORM fn_error(404, 'CUADRANTE_NO_ENCONTRADO', 'El cuadrante no existe o ya está deshabilitado');
    END IF;

    WITH previas AS (
        SELECT id, estado FROM alertas
        WHERE cuadrante_id = p_cuadrante AND estado IN ('ACTIVA', 'ATENDIDA')
        FOR UPDATE
    ), cerradas AS (
        UPDATE alertas a SET estado = 'RESUELTA', actualizada_en = clock_timestamp()
        FROM previas p WHERE a.id = p.id
        RETURNING a.id, p.estado AS anterior
    ), historial AS (
        INSERT INTO alertas_historial (alerta_id, estado_anterior, estado_nuevo, autor_sistema)
        SELECT id, anterior, 'RESUELTA', true FROM cerradas
    )
    SELECT coalesce(jsonb_agg(id), '[]'::jsonb) INTO v_alertas FROM cerradas;

    WITH afectados AS (
        SELECT d.id, d.usuario_id, fn_cuadrante_de_punto(d.punto) AS nuevo
        FROM domicilios d WHERE d.vigente AND d.cuadrante_id = p_cuadrante
    ), actualizados AS (
        UPDATE domicilios d SET cuadrante_id = a.nuevo, actualizado_en = clock_timestamp()
        FROM afectados a WHERE d.id = a.id
        RETURNING a.usuario_id, a.nuevo
    )
    SELECT coalesce(jsonb_agg(jsonb_build_object('usuarioId', usuario_id, 'cuadranteId', nuevo))
                    FILTER (WHERE nuevo IS NOT NULL), '[]'::jsonb),
           coalesce(jsonb_agg(usuario_id) FILTER (WHERE nuevo IS NULL), '[]'::jsonb)
    INTO v_reasignados, v_sin
    FROM actualizados;

    RETURN jsonb_build_object(
        'cuadranteId', p_cuadrante,
        'alertasCerradas', v_alertas,
        'reasignados', v_reasignados,
        'sinCuadrante', v_sin,
        'dispositivosSinCuadrante', coalesce((
            SELECT jsonb_agg(DISTINCT token_push) FROM dispositivos
            WHERE valido AND usuario_id IN (SELECT (x)::uuid FROM jsonb_array_elements_text(v_sin) x)), '[]'::jsonb),
        'dispositivosReasignados', coalesce((
            SELECT jsonb_agg(DISTINCT token_push) FROM dispositivos
            WHERE valido AND usuario_id IN (SELECT (x->>'usuarioId')::uuid FROM jsonb_array_elements(v_reasignados) x)), '[]'::jsonb));
END
$$;

CREATE OR REPLACE FUNCTION sp_cuadrante_habilitar(p_admin uuid, p_cuadrante uuid)
RETURNS jsonb
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
DECLARE
    v_reasignados jsonb;
BEGIN
    PERFORM fn_exigir_admin(p_admin);
    UPDATE cuadrantes SET habilitado = true, actualizado_en = clock_timestamp()
    WHERE id = p_cuadrante AND activo AND NOT habilitado;
    IF NOT FOUND THEN
        PERFORM fn_error(404, 'CUADRANTE_NO_ENCONTRADO', 'El cuadrante no existe o ya está habilitado');
    END IF;

    WITH actualizados AS (
        UPDATE domicilios d SET cuadrante_id = fn_cuadrante_de_punto(d.punto), actualizado_en = clock_timestamp()
        WHERE d.vigente AND d.cuadrante_id IS NULL AND fn_cuadrante_de_punto(d.punto) IS NOT NULL
        RETURNING d.usuario_id
    )
    SELECT coalesce(jsonb_agg(usuario_id), '[]'::jsonb) INTO v_reasignados FROM actualizados;

    RETURN jsonb_build_object(
        'cuadranteId', p_cuadrante,
        'reasignados', v_reasignados,
        'dispositivos', coalesce((
            SELECT jsonb_agg(DISTINCT token_push) FROM dispositivos
            WHERE valido AND usuario_id IN (SELECT (x)::uuid FROM jsonb_array_elements_text(v_reasignados) x)), '[]'::jsonb));
END
$$;
