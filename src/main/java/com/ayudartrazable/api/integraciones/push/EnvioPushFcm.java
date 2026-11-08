package com.ayudartrazable.api.integraciones.push;

import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class EnvioPushFcm implements EnvioPush {

    private static final Logger LOG = LoggerFactory.getLogger(EnvioPushFcm.class);

    private final RestClient cliente;
    private final String proyecto;
    private final Supplier<String> tokenDeAcceso;

    public EnvioPushFcm(RestClient.Builder builder, String proyecto, Supplier<String> tokenDeAcceso) {
        this.cliente = builder.build();
        this.proyecto = proyecto;
        this.tokenDeAcceso = tokenDeAcceso;
    }

    @Override
    public ResultadoPush enviar(String tokenDispositivo, NotificacionPush notificacion) {
        Map<String, Object> mensaje = Map.of("message", Map.of(
                "token", tokenDispositivo,
                "notification", Map.of("title", notificacion.titulo(), "body", notificacion.cuerpo()),
                "data", notificacion.datos(),
                "android", Map.of("priority", "high")));
        try {
            cliente.post().uri("/v1/projects/{proyecto}/messages:send", proyecto)
                    .header("Authorization", "Bearer " + tokenDeAcceso.get())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mensaje)
                    .retrieve()
                    .toBodilessEntity();
            return ResultadoPush.ENVIADO;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND || e.getStatusCode() == HttpStatus.BAD_REQUEST
                    || e.getStatusCode() == HttpStatus.FORBIDDEN) {
                return ResultadoPush.TOKEN_INVALIDO;
            }
            LOG.warn("FCM rechazó el envío: estado {}", e.getStatusCode().value());
            return ResultadoPush.ERROR_TEMPORAL;
        } catch (RestClientException e) {
            LOG.warn("FCM no disponible: {}", e.getClass().getSimpleName());
            return ResultadoPush.ERROR_TEMPORAL;
        }
    }
}
