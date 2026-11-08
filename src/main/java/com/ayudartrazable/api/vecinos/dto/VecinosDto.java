package com.ayudartrazable.api.vecinos.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class VecinosDto {

    private VecinosDto() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CuadranteResumen(UUID cuadranteId, String codigo, String nombre) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Verificaciones(boolean dni, boolean celular, boolean domicilio) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Perfil(String tipo, String dni, String nombres, String apellidoPaterno, String apellidoMaterno,
            Integer edad, String sexo, String celular, CuadranteResumen cuadrante, String referenciaCuadra,
            Verificaciones verificaciones, boolean sinCobertura, String insignia) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Telefono(String servicio, String numero) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelefonosEmergencia(String fuente, String distrito, List<Telefono> telefonos) {
    }

    public record SolicitudDispositivo(
            @NotBlank(message = "Falta el identificador de notificaciones") @Size(max = 4096, message = "Identificador demasiado largo") String tokenPush) {

        @Override
        public String toString() {
            return "SolicitudDispositivo[...]";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DispositivoRegistrado(boolean registrado) {
    }

    public record SolicitudReporteDatos(
            @NotBlank(message = "Cuéntanos qué dato es incorrecto") @Size(max = 500, message = "Máximo 500 caracteres") String descripcion) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReporteRecibido(boolean recibido, String mensaje) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VecinoVisible(String nombre, String referenciaCuadra, String insignia) {
    }
}
