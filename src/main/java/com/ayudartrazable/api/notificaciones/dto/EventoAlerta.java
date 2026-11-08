package com.ayudartrazable.api.notificaciones.dto;

import tools.jackson.databind.JsonNode;

public record EventoAlerta(String evento, JsonNode alerta) {

    public static final String ALERTA_CREADA = "ALERTA_CREADA";
    public static final String ALERTA_ACTUALIZADA = "ALERTA_ACTUALIZADA";
}
