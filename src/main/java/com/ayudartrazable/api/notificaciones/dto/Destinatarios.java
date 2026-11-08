package com.ayudartrazable.api.notificaciones.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Destinatarios(List<UUID> cuadrantes, List<UUID> coordinadores, List<UUID> serenazgo,
        List<String> dispositivos) {

    public static Destinatarios vacios() {
        return new Destinatarios(List.of(), List.of(), List.of(), List.of());
    }
}
