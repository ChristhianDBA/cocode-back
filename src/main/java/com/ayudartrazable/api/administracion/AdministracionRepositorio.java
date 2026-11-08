package com.ayudartrazable.api.administracion;

import com.ayudartrazable.api.comun.bd.LlamadorSp;
import com.ayudartrazable.api.integraciones.registrocivil.DatosCiviles;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;

@Repository
public class AdministracionRepositorio {

    private final LlamadorSp sp;

    public AdministracionRepositorio(LlamadorSp sp) {
        this.sp = sp;
    }

    public JsonNode buscarUsuario(UUID admin, String dni) {
        return sp.llamar("fn_admin_buscar_usuario", admin, dni);
    }

    public JsonNode asignarCoordinador(UUID admin, UUID usuario, List<UUID> cuadrantes) {
        return sp.llamar("sp_coordinador_asignar", admin, usuario, cuadrantes.toArray(UUID[]::new));
    }

    public JsonNode retirarCoordinador(UUID admin, UUID usuario, UUID cuadrante) {
        return sp.llamar("sp_coordinador_retirar", admin, usuario, cuadrante);
    }

    public JsonNode altaSerenazgo(UUID admin, DatosCiviles d, String celular, int distrito) {
        return sp.llamar("sp_serenazgo_alta", admin, d.dni(), d.nombres(), d.apellidoPaterno(), d.apellidoMaterno(),
                d.fechaNacimiento(), d.sexo(), celular, distrito);
    }

    public JsonNode desactivarSerenazgo(UUID admin, UUID usuario) {
        return sp.llamar("sp_serenazgo_desactivar", admin, usuario);
    }

    public JsonNode avisos(UUID admin) {
        return sp.llamar("fn_avisos_administrador", admin);
    }

    public JsonNode levantarSuspension(UUID admin, UUID suspension) {
        return sp.llamar("sp_suspension_levantar", admin, suspension);
    }

    public JsonNode deshabilitarCuadrante(UUID admin, UUID cuadrante) {
        return sp.llamar("sp_cuadrante_deshabilitar", admin, cuadrante);
    }

    public JsonNode habilitarCuadrante(UUID admin, UUID cuadrante) {
        return sp.llamar("sp_cuadrante_habilitar", admin, cuadrante);
    }
}
