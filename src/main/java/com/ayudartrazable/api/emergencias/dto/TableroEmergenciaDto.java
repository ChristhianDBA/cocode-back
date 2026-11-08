package com.ayudartrazable.api.emergencias.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class TableroEmergenciaDto {

    private TableroEmergenciaDto() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ServicioLocalItem(
            String id,
            String iniciales,
            String nombre,
            String detalle,
            String distancia,
            String telefono,
            String bgAvatar,
            String colorAvatar) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DirectorioLocalResponse(
            String distrito,
            String cuadrante,
            List<ServicioLocalItem> servicios) {
    }

    public record SolicitudActivarTablero(
            @NotBlank(message = "El tipo de emergencia es obligatorio") String tipoEmergencia,
            @NotBlank(message = "Describe qué está pasando") String descripcion,
            String cuadrante) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EstadoTableroResponse(
            String id,
            String cuadrante,
            String tipoEmergencia,
            String descripcion,
            String estado,
            int confirmacionesActuales,
            int confirmacionesRequeridas,
            int minutosRestantes,
            boolean activo,
            int personasASalvoCount) {
    }

    public record RespuestaConfirmacion(
            boolean exitoso,
            int confirmacionesActuales,
            boolean activado,
            String mensaje) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NecesidadItem(
            String id,
            String nombre,
            String prioridad,
            int reunido,
            int meta,
            String unidad,
            boolean aportado) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AporteItem(
            String id,
            String nombre,
            String vecino,
            String tiempo,
            boolean contactado) {
    }

    public record SolicitudAporte(
            String necesidadId,
            @NotBlank String descripcion,
            int cantidad) {
    }
}
