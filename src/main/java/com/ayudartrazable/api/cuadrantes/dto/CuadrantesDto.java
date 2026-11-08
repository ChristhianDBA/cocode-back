package com.ayudartrazable.api.cuadrantes.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

public final class CuadrantesDto {

    private CuadrantesDto() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PuntoResuelto(boolean dentroDeCobertura, UUID cuadranteId, String codigo, String nombre,
            String distrito, String direccionEstimada) {

        public PuntoResuelto conDireccion(String direccion) {
            return new PuntoResuelto(dentroDeCobertura, cuadranteId, codigo, nombre, distrito, direccion);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PinMapa(
            String id,
            String initials,
            String name,
            String trade,
            int vouchCount,
            String distance,
            String walkingTime,
            double lat,
            double lng,
            String category,
            String tag) {
    }
}
