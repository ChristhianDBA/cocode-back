package com.ayudartrazable.api.integraciones.push;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class EnvioPushSimulado implements EnvioPush {

    private final Map<String, List<NotificacionPush>> enviados = new ConcurrentHashMap<>();
    private final Set<String> invalidos = ConcurrentHashMap.newKeySet();

    @Override
    public ResultadoPush enviar(String tokenDispositivo, NotificacionPush notificacion) {
        if (invalidos.contains(tokenDispositivo)) {
            return ResultadoPush.TOKEN_INVALIDO;
        }
        enviados.computeIfAbsent(tokenDispositivo, t -> new CopyOnWriteArrayList<>()).add(notificacion);
        return ResultadoPush.ENVIADO;
    }

    public void marcarInvalido(String tokenDispositivo) {
        invalidos.add(tokenDispositivo);
    }

    public List<NotificacionPush> enviadosA(String tokenDispositivo) {
        return List.copyOf(enviados.getOrDefault(tokenDispositivo, List.of()));
    }

    public void limpiar() {
        enviados.clear();
        invalidos.clear();
    }
}
