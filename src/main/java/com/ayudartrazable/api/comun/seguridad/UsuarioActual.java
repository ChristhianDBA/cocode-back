package com.ayudartrazable.api.comun.seguridad;

import java.util.List;
import java.util.UUID;

public record UsuarioActual(UUID usuarioId, UUID sesionId, String tipo, String nombres, String sexo, UUID cuadranteId,
        Integer distritoId, List<String> roles) {

    public boolean tieneRol(String rol) {
        return roles != null && roles.contains(rol);
    }
}
