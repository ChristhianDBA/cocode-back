CREATE OR REPLACE FUNCTION sp_recuperacion_completar(p_token text)
RETURNS jsonb
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_t       altas_en_curso := fn_tramite(p_token);
    v_usuario usuarios%ROWTYPE;
    v_aplica  timestamptz;
BEGIN
    IF v_t.proposito <> 'RECUPERACION' THEN
        PERFORM fn_error(409, 'TRAMITE_INVALIDO', 'Tu registro no es válido. Vuelve a empezar');
    END IF;
    IF v_t.celular_verificado_en IS NULL THEN
        PERFORM fn_error(409, 'FALTA_CELULAR', 'Falta verificar tu celular');
    END IF;
    SELECT * INTO v_usuario FROM usuarios WHERE dni = v_t.dni AND activo AND tipo = 'VECINO' FOR UPDATE;
    IF NOT FOUND THEN
        PERFORM fn_error(409, 'RECUPERACION_INVALIDA', 'No pudimos recuperar una cuenta con estos datos');
    END IF;
    IF EXISTS (SELECT 1 FROM usuarios WHERE celular = v_t.celular AND id <> v_usuario.id) THEN
        PERFORM fn_error(409, 'CELULAR_REGISTRADO', 'Este celular ya está registrado en otra cuenta');
    END IF;
    IF v_t.cancelada_en IS NOT NULL THEN
        PERFORM fn_error(409, 'RECUPERACION_CANCELADA', 'El titular de la cuenta canceló la recuperación');
    END IF;

    IF v_t.aplica_en IS NULL THEN
        v_aplica := clock_timestamp() + make_interval(hours => fn_parametro_entero('recuperacion_espera_horas', 24));
        UPDATE altas_en_curso
        SET aplica_en = v_aplica, usuario_id = v_usuario.id,
            vence_en = greatest(vence_en, v_aplica + interval '24 hours'), actualizado_en = clock_timestamp()
        WHERE id = v_t.id;
        RETURN jsonb_build_object(
            'pendiente', true,
            'aplicaEn', v_aplica,
            'mensaje', format('Tu solicitud quedó registrada. Por seguridad, avisamos al celular anterior y podrás completar la recuperación a partir del %s',
                              to_char(v_aplica AT TIME ZONE 'America/Lima', 'DD/MM/YYYY "a las" HH24:MI')),
            'usuarioId', v_usuario.id,
            'celularAnterior', v_usuario.celular,
            'avisoTitular', format('Ayudar Trazable: se solicitó recuperar tu cuenta con otro celular. Si no fuiste tú, entra a la app y cancela la recuperación antes del %s',
                                   to_char(v_aplica AT TIME ZONE 'America/Lima', 'DD/MM/YYYY HH24:MI')),
            'dispositivos', coalesce((SELECT jsonb_agg(DISTINCT token_push) FROM dispositivos
                                      WHERE valido AND usuario_id = v_usuario.id), '[]'::jsonb));
    END IF;

    IF v_t.aplica_en > clock_timestamp() THEN
        PERFORM fn_error(409, 'RECUPERACION_EN_ESPERA', format('Podrás completar la recuperación a partir del %s',
            to_char(v_t.aplica_en AT TIME ZONE 'America/Lima', 'DD/MM/YYYY "a las" HH24:MI')));
    END IF;

    PERFORM sp_sesiones_revocar(v_usuario.id, NULL, 'RECUPERACION');
    UPDATE codigos_verificacion SET activo = false WHERE referencia = v_usuario.id AND activo;
    UPDATE usuarios
    SET celular = v_t.celular, nombres = v_t.nombres, apellido_paterno = v_t.apellido_paterno,
        apellido_materno = v_t.apellido_materno, fecha_nacimiento = v_t.fecha_nacimiento, sexo = v_t.sexo,
        actualizado_en = clock_timestamp()
    WHERE id = v_usuario.id;
    UPDATE altas_en_curso SET completada_en = clock_timestamp(), actualizado_en = clock_timestamp()
    WHERE id = v_t.id;
    INSERT INTO avisos_administrador (tipo, usuario_id, referencia_id, detalle)
    VALUES ('RECUPERACION', v_usuario.id, v_t.id, 'Un vecino recuperó su cuenta con un celular nuevo');

    RETURN sp_sesion_crear(v_usuario.id) || jsonb_build_object('pendiente', false);
END
$$;

CREATE OR REPLACE FUNCTION sp_recuperacion_cancelar(p_usuario uuid)
RETURNS jsonb
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_total integer;
BEGIN
    UPDATE altas_en_curso a
    SET cancelada_en = clock_timestamp(), actualizado_en = clock_timestamp()
    FROM usuarios u
    WHERE u.id = p_usuario AND u.activo AND a.dni = u.dni
      AND a.proposito = 'RECUPERACION' AND a.aplica_en IS NOT NULL
      AND a.cancelada_en IS NULL AND a.completada_en IS NULL AND a.activo;
    GET DIAGNOSTICS v_total = ROW_COUNT;
    RETURN jsonb_build_object(
        'canceladas', v_total,
        'mensaje', CASE WHEN v_total > 0 THEN 'Cancelaste la recuperación de tu cuenta. Tu celular actual sigue vigente'
                        ELSE 'No hay recuperaciones pendientes de tu cuenta' END);
END
$$;
