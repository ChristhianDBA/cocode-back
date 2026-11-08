package com.ayudartrazable.api.emergencias.dto;

import com.ayudartrazable.api.notificaciones.dto.Destinatarios;
import com.ayudartrazable.api.vecinos.dto.VecinosDto.TelefonosEmergencia;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

public final class EmergenciasDto {

    private EmergenciasDto() {
    }

    public enum TipoReporte {
        SOSPECHA,
        ROBO,
        MEDICA,
        ALUMBRADO
    }

    public enum EstadoAlerta {
        ATENDIDA,
        FALSA_ALARMA,
        RESUELTA
    }

    public record SolicitudPanico(
            Boolean confirmado,
            @DecimalMin(value = "-90", message = "Latitud no válida") @DecimalMax(value = "90", message = "Latitud no válida") Double latitud,
            @DecimalMin(value = "-180", message = "Longitud no válida") @DecimalMax(value = "180", message = "Longitud no válida") Double longitud) {
    }

    public record SolicitudReporte(
            @NotNull(message = "Elige el tipo de reporte") TipoReporte tipo,
            @NotBlank(message = "Describe brevemente la situación") @Size(max = 500, message = "Máximo 500 caracteres") String descripcion) {
    }

    public record SolicitudEstado(@NotNull(message = "Indica el nuevo estado") EstadoAlerta estado) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResultadoAlerta(JsonNode alerta, JsonNode alertaConUbicacion, Destinatarios destinatarios,
            Boolean creada, Boolean fueraDeCobertura, Boolean emisorSuspendido) {

        public boolean fueCreada() {
            return Boolean.TRUE.equals(creada);
        }

        public boolean estaFueraDeCobertura() {
            return Boolean.TRUE.equals(fueraDeCobertura);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RespuestaPanico(boolean creada, boolean fueraDeCobertura, JsonNode alerta,
            TelefonosEmergencia telefonos) {
    }
}
