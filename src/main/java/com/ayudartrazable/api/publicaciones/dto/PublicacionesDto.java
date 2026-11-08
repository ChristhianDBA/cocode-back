package com.ayudartrazable.api.publicaciones.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class PublicacionesDto {

    private PublicacionesDto() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PublicacionItem(
            String id,
            String iniciales,
            String nombre,
            String rubro,
            String detalle,
            String distancia,
            int vistosBuenos,
            double calificacion,
            String bgAvatar,
            String colorAvatar,
            String categoria,
            String tipo,
            String ubicacion,
            String metodoContacto,
            List<String> condiciones,
            String fechaPublicacion) {
    }

    public record SolicitudPublicacion(
            @NotBlank(message = "El tipo es obligatorio") String tipo,
            @NotBlank(message = "El título es obligatorio") String titulo,
            String descripcion,
            String ubicacion,
            String metodoContacto,
            List<String> condiciones) {
    }

    public record SolicitudFavor(
            String mensaje,
            String fechaDevolucion) {
    }

    public record RespuestaSolicitudFavor(
            boolean exitoso,
            String mensaje,
            String favorId,
            String chatSugerido) {
    }

    public record CambioEstado(
            @NotBlank(message = "El estado es requerido") String estado) {
    }

    public record ItemActividad(
            String id,
            String tipo,
            String titulo,
            String descripcion,
            String estado,
            String fecha,
            int interesadosCount,
            String prestadoA,
            String hastaFecha) {
    }
}
