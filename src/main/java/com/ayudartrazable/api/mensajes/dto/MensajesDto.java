package com.ayudartrazable.api.mensajes.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

public final class MensajesDto {

    private MensajesDto() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ConversacionItem(
            String id,
            String avatarInitials,
            String avatarBg,
            String avatarColor,
            String name,
            String subtitle,
            String lastMessage,
            String time,
            int unreadCount,
            boolean isOfficial) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MensajeBubble(
            String id,
            String conversacionId,
            String texto,
            String remitente,
            String hora,
            boolean leido) {
    }

    public record SolicitudEnviarMensaje(
            @NotBlank(message = "El mensaje no puede estar vacío") String texto) {
    }

    public record SolicitudIniciarChat(
            @NotBlank String vecinoId,
            String publicacionId,
            String mensajeInicial) {
    }

    public record ReporteUsuario(
            @NotBlank String motivo) {
    }
}
