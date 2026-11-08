package com.ayudartrazable.api.integraciones.push;

import java.util.Map;

public record NotificacionPush(String titulo, String cuerpo, Map<String, String> datos) {
}
