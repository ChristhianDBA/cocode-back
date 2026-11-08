package com.ayudartrazable.api.notificaciones;

import com.ayudartrazable.api.integraciones.push.EnvioPush;
import com.ayudartrazable.api.integraciones.push.NotificacionPush;
import com.ayudartrazable.api.integraciones.push.ResultadoPush;
import com.ayudartrazable.api.notificaciones.dto.Destinatarios;
import com.ayudartrazable.api.notificaciones.dto.EventoAlerta;
import com.ayudartrazable.api.vecinos.VecinosRepositorio;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class DifusionAlertas {

    private static final Logger LOG = LoggerFactory.getLogger(DifusionAlertas.class);
    public static final String COLA_USUARIO = "/queue/alertas";

    private final SimpMessagingTemplate mensajeria;
    private final EnvioPush push;
    private final VecinosRepositorio vecinos;
    private final Executor ejecutor;

    public DifusionAlertas(SimpMessagingTemplate mensajeria, EnvioPush push, VecinosRepositorio vecinos,
            @Qualifier("ejecutorPush") Executor ejecutor) {
        this.mensajeria = mensajeria;
        this.push = push;
        this.vecinos = vecinos;
        this.ejecutor = ejecutor;
    }

    public void difundir(String evento, JsonNode alerta, JsonNode alertaConUbicacion, Destinatarios destinatarios,
            boolean conPush) {
        EventoAlerta publico = new EventoAlerta(evento, alerta);
        EventoAlerta completo = new EventoAlerta(evento, alertaConUbicacion);
        destinatarios.cuadrantes().forEach(c -> mensajeria.convertAndSend("/topic/cuadrante/" + c, publico));
        destinatarios.coordinadores().forEach(u -> mensajeria.convertAndSendToUser(u.toString(), COLA_USUARIO, publico));
        destinatarios.serenazgo().forEach(u -> mensajeria.convertAndSendToUser(u.toString(), COLA_USUARIO, completo));
        if (conPush) {
            enviarPush(destinatarios.dispositivos(), notificacion(evento, alerta));
        }
    }

    public void avisarCuadrante(UUID cuadrante, String evento, JsonNode datos) {
        mensajeria.convertAndSend("/topic/cuadrante/" + cuadrante, new EventoAlerta(evento, datos));
    }

    public void enviarPush(List<String> dispositivos, NotificacionPush notificacion) {
        if (dispositivos == null || dispositivos.isEmpty()) {
            return;
        }
        ejecutor.execute(() -> dispositivos.forEach(token -> {
            try {
                if (push.enviar(token, notificacion) == ResultadoPush.TOKEN_INVALIDO) {
                    vecinos.invalidarDispositivo(token);
                }
            } catch (RuntimeException e) {
                LOG.warn("No se pudo enviar una notificación push: {}", e.getClass().getSimpleName());
            }
        }));
    }

    static NotificacionPush notificacion(String evento, JsonNode alerta) {
        String tipo = alerta.path("tipo").asString("");
        String titulo = switch (tipo) {
            case "PANICO" -> "Alerta de pánico";
            case "SOSPECHA" -> "Reporte vecinal: actividad sospechosa";
            case "ROBO" -> "Reporte vecinal: robo";
            case "MEDICA" -> "Reporte vecinal: emergencia médica";
            case "ALUMBRADO" -> "Reporte vecinal: alumbrado";
            default -> "Aviso de tu barrio";
        };
        if (EventoAlerta.ALERTA_ACTUALIZADA.equals(evento)) {
            titulo = titulo + " · " + alerta.path("estado").asString("");
        }
        String cuerpo = alerta.path("emisor").asString("") + " · " + alerta.path("referenciaCuadra").asString("");
        return new NotificacionPush(titulo, cuerpo, Map.of(
                "evento", evento,
                "alertaId", alerta.path("alertaId").asString(""),
                "tipo", tipo));
    }
}
