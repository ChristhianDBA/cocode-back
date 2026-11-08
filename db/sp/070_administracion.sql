CREATE OR REPLACE FUNCTION fn_exigir_admin(p_admin uuid)
RETURNS void
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM usuarios WHERE id = p_admin AND activo AND tipo = 'ADMIN') THEN
        PERFORM fn_error(403, 'SIN_PERMISO', 'No tienes permiso para realizar esta acción');
    END IF;
END
$$;

CREATE OR REPLACE FUNCTION sp_admin_inicial(p_dni text, p_nombres text, p_apellido_paterno text, p_apellido_materno text,
                                            p_fecha_nacimiento date, p_sexo text, p_celular text)
RETURNS jsonb
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
DECLARE
    v_id uuid;
BEGIN
    IF EXISTS (SELECT 1 FROM usuarios WHERE tipo = 'ADMIN') THEN
        PERFORM fn_error(409, 'ADMIN_EXISTENTE', 'Ya existe un administrador');
    END IF;
    INSERT INTO usuarios (dni, nombres, apellido_paterno, apellido_materno, fecha_nacimiento, sexo, celular, tipo)
    VALUES (p_dni, p_nombres, p_apellido_paterno, p_apellido_materno, p_fecha_nacimiento, p_sexo, p_celular, 'ADMIN')
    RETURNING id INTO v_id;
    RETURN jsonb_build_object('usuarioId', v_id);
END
$$;
REVOKE EXECUTE ON FUNCTION sp_admin_inicial(text, text, text, text, date, text, text) FROM app_ayudar;

CREATE OR REPLACE FUNCTION sp_coordinador_asignar(p_admin uuid, p_usuario uuid, p_cuadrantes uuid[])
RETURNS jsonb
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
DECLARE
    v_total integer;
BEGIN
    PERFORM fn_exigir_admin(p_admin);
    IF p_cuadrantes IS NULL OR cardinality(p_cuadrantes) = 0 THEN
        PERFORM fn_error(400, 'CUADRANTES_REQUERIDOS', 'Indica al menos un cuadrante');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM usuarios u JOIN domicilios d ON d.usuario_id = u.id AND d.vigente
                   WHERE u.id = p_usuario AND u.activo AND u.tipo = 'VECINO') THEN
        PERFORM fn_error(409, 'NO_VERIFICADO', 'Solo un vecino verificado puede ser coordinador');
    END IF;
    IF EXISTS (SELECT 1 FROM unnest(p_cuadrantes) x(id)
               WHERE NOT EXISTS (SELECT 1 FROM cuadrantes c WHERE c.id = x.id AND c.activo)) THEN
        PERFORM fn_error(404, 'CUADRANTE_NO_ENCONTRADO', 'Uno de los cuadrantes no existe');
    END IF;

    INSERT INTO roles_coordinador (usuario_id, cuadrante_id, otorgado_por)
    SELECT p_usuario, x.id, p_admin FROM unnest(p_cuadrantes) x(id)
    WHERE NOT EXISTS (SELECT 1 FROM roles_coordinador r WHERE r.usuario_id = p_usuario AND r.cuadrante_id = x.id AND r.activo);
    GET DIAGNOSTICS v_total = ROW_COUNT;
    RETURN jsonb_build_object('asignados', v_total);
END
$$;

CREATE OR REPLACE FUNCTION sp_coordinador_retirar(p_admin uuid, p_usuario uuid, p_cuadrante uuid)
RETURNS jsonb
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
DECLARE
    v_total integer;
BEGIN
    PERFORM fn_exigir_admin(p_admin);
    UPDATE roles_coordinador SET activo = false, retirado_en = clock_timestamp()
    WHERE usuario_id = p_usuario AND activo AND (p_cuadrante IS NULL OR cuadrante_id = p_cuadrante);
    GET DIAGNOSTICS v_total = ROW_COUNT;
    RETURN jsonb_build_object('retirados', v_total);
END
$$;

CREATE OR REPLACE FUNCTION sp_serenazgo_alta(p_admin uuid, p_dni text, p_nombres text, p_apellido_paterno text,
                                             p_apellido_materno text, p_fecha_nacimiento date, p_sexo text,
                                             p_celular text, p_distrito integer)
RETURNS jsonb
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
DECLARE
    v_id uuid;
BEGIN
    PERFORM fn_exigir_admin(p_admin);
    IF p_celular IS NULL OR p_celular !~ '^9\d{8}$' THEN
        PERFORM fn_error(400, 'CELULAR_INVALIDO', 'El celular debe tener 9 dígitos y empezar con 9');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM distritos WHERE id = p_distrito AND activo) THEN
        PERFORM fn_error(404, 'DISTRITO_NO_ENCONTRADO', 'El distrito no existe');
    END IF;
    IF EXISTS (SELECT 1 FROM usuarios WHERE dni = p_dni) THEN
        PERFORM fn_error(409, 'DNI_REGISTRADO', 'Este DNI ya está registrado');
    END IF;
    IF EXISTS (SELECT 1 FROM usuarios WHERE celular = p_celular) THEN
        PERFORM fn_error(409, 'CELULAR_REGISTRADO', 'Este celular ya está registrado en otra cuenta');
    END IF;
    INSERT INTO usuarios (dni, nombres, apellido_paterno, apellido_materno, fecha_nacimiento, sexo, celular, tipo)
    VALUES (p_dni, p_nombres, p_apellido_paterno, p_apellido_materno, p_fecha_nacimiento, p_sexo, p_celular, 'SERENAZGO')
    RETURNING id INTO v_id;
    INSERT INTO cuentas_serenazgo (usuario_id, distrito_id) VALUES (v_id, p_distrito);
    RETURN jsonb_build_object('usuarioId', v_id, 'distritoId', p_distrito);
END
$$;

CREATE OR REPLACE FUNCTION sp_serenazgo_desactivar(p_admin uuid, p_usuario uuid)
RETURNS jsonb
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
BEGIN
    PERFORM fn_exigir_admin(p_admin);
    UPDATE cuentas_serenazgo SET activo = false, actualizado_en = clock_timestamp() WHERE usuario_id = p_usuario AND activo;
    IF NOT FOUND THEN
        PERFORM fn_error(404, 'SERENAZGO_NO_ENCONTRADO', 'La cuenta de serenazgo no existe o ya está desactivada');
    END IF;
    UPDATE usuarios SET activo = false, actualizado_en = clock_timestamp() WHERE id = p_usuario;
    PERFORM sp_sesiones_revocar(p_usuario, NULL, 'DESACTIVACION');
    RETURN jsonb_build_object('desactivado', true);
END
$$;

CREATE OR REPLACE FUNCTION fn_avisos_administrador(p_admin uuid)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
BEGIN
    PERFORM fn_exigir_admin(p_admin);
    RETURN coalesce((
        SELECT jsonb_agg(jsonb_build_object(
                   'avisoId', a.id,
                   'tipo', a.tipo,
                   'usuarioId', a.usuario_id,
                   'vecino', fn_nombre_abreviado(u.nombres, u.apellido_paterno),
                   'referenciaId', a.referencia_id,
                   'detalle', a.detalle,
                   'creadoEn', a.creado_en)
               ORDER BY a.creado_en DESC)
        FROM avisos_administrador a JOIN usuarios u ON u.id = a.usuario_id
        WHERE a.activo AND NOT a.leido), '[]'::jsonb);
END
$$;

CREATE OR REPLACE FUNCTION sp_suspension_levantar(p_admin uuid, p_suspension uuid)
RETURNS jsonb
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
DECLARE
    v_usuario uuid;
BEGIN
    PERFORM fn_exigir_admin(p_admin);
    UPDATE suspensiones_panico SET levantada_por = p_admin, levantada_en = clock_timestamp()
    WHERE id = p_suspension AND levantada_en IS NULL
    RETURNING usuario_id INTO v_usuario;
    IF v_usuario IS NULL THEN
        PERFORM fn_error(404, 'SUSPENSION_NO_ENCONTRADA', 'La suspensión no existe o ya fue levantada');
    END IF;
    UPDATE avisos_administrador SET leido = true WHERE tipo = 'SUSPENSION' AND referencia_id = p_suspension;
    RETURN jsonb_build_object('levantada', true, 'usuarioId', v_usuario);
END
$$;

CREATE OR REPLACE FUNCTION fn_admin_buscar_usuario(p_admin uuid, p_dni text)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
BEGIN
    PERFORM fn_exigir_admin(p_admin);
    RETURN (
        SELECT jsonb_build_object(
            'usuarioId', u.id,
            'nombre', fn_nombre_abreviado(u.nombres, u.apellido_paterno),
            'tipo', u.tipo,
            'activo', u.activo,
            'cuadrante', c.nombre,
            'cuadrantesCoordinados', coalesce((
                SELECT jsonb_agg(r.cuadrante_id) FROM roles_coordinador r WHERE r.usuario_id = u.id AND r.activo), '[]'::jsonb))
        FROM usuarios u
        LEFT JOIN domicilios d ON d.usuario_id = u.id AND d.vigente
        LEFT JOIN cuadrantes c ON c.id = d.cuadrante_id
        WHERE u.dni = p_dni);
END
$$;
