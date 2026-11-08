package com.ayudartrazable.api.vecinos.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class ResenasDto {

    private ResenasDto() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PerfilVecinoPublico(
            String id,
            String iniciales,
            String nombre,
            String oficio,
            int aniosOficio,
            String distancia,
            String ubicacion,
            int vistosBuenos,
            double calificacion,
            int resenasCount,
            String fechaRegistro,
            boolean tieneMiVistoBueno) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResenaItem(
            String id,
            String autorNombre,
            String autorIniciales,
            String autorManzana,
            int calificacion,
            boolean recomendo,
            List<String> destacados,
            String comentario,
            String fecha,
            boolean verificado) {
    }

    public record SolicitudResena(
            @Min(1) @Max(5) int calificacion,
            boolean recomendo,
            List<String> destacados,
            @NotBlank(message = "Por favor ingresa un comentario") String comentario) {
    }

    public record SolicitudOcupacion(
            @NotBlank String oficio,
            int aniosExperiencia,
            boolean visibleDirectorio) {
    }

    public record RespuestaVistoBueno(
            boolean tieneMiVistoBueno,
            int totalVistosBuenos) {
    }
}
