package com.ayudartrazable.api.integraciones.push;

public interface EnvioPush {

    ResultadoPush enviar(String tokenDispositivo, NotificacionPush notificacion);
}
