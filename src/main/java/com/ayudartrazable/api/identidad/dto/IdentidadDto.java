package com.ayudartrazable.api.identidad.dto;

import com.ayudartrazable.api.integraciones.mensajeria.Canal;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class IdentidadDto {

    private IdentidadDto() {
    }

    public enum Origen {
        ESCANEO,
        MANUAL
    }

    public record SolicitudDni(
            @NotNull(message = "Ingresa tu DNI") @Pattern(regexp = "\\d{8}", message = "El DNI debe tener 8 dígitos") String dni,
            Origen origen,
            LocalDate fechaNacimiento,
            String sexo) {

        @Override
        public String toString() {
            return "SolicitudDni[origen=" + origen + "]";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DatosCivilesTramite(String dni, String nombres, String apellidoPaterno, String apellidoMaterno,
            Integer edad, String sexo, boolean verificado, boolean editable) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RespuestaTramite(String tokenTramite, OffsetDateTime venceEn, DatosCivilesTramite datos) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EstadoTramite(String proposito, OffsetDateTime venceEn, DatosCivilesTramite datos,
            boolean celularVerificado, boolean domicilioGuardado, String referenciaCuadra, List<String> faltan,
            boolean puedeRegistrar) {
    }

    public record SolicitudCodigo(
            @NotNull(message = "Ingresa tu celular") @Pattern(regexp = "9\\d{8}", message = "El celular debe tener 9 dígitos y empezar con 9") String celular,
            @NotNull(message = "Elige SMS o WhatsApp") Canal canal) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CodigoEmitido(UUID codigoId, String codigo, String celular, Canal canal, int minutosDeVigencia,
            int venceEnSegundos, int reenvioEnSegundos) {

        @Override
        public String toString() {
            return "CodigoEmitido[codigoId=" + codigoId + "]";
        }
    }

    public record RespuestaCodigo(int venceEnSegundos, int reenvioEnSegundos) {
    }

    public record SolicitudVerificacion(
            @NotNull(message = "Ingresa el código") @Pattern(regexp = "\\d{4}", message = "El código tiene 4 dígitos") String codigo,
            String celular) {

        public SolicitudVerificacion(String codigo) {
            this(codigo, null);
        }

        @Override
        public String toString() {
            return "SolicitudVerificacion[****]";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RespuestaVerificacion(boolean celularVerificado) {
    }

    public record SolicitudDomicilio(
            @NotNull(message = "Ubica tu casa en el mapa") @DecimalMin(value = "-90", message = "Latitud no válida") @DecimalMax(value = "90", message = "Latitud no válida") Double latitud,
            @NotNull(message = "Ubica tu casa en el mapa") @DecimalMin(value = "-180", message = "Longitud no válida") @DecimalMax(value = "180", message = "Longitud no válida") Double longitud) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RespuestaDomicilio(UUID cuadranteId, String codigo, String nombre, String direccion,
            String referenciaCuadra, String mensajePrivacidad) {
    }

    public record SolicitudAlta(Boolean aceptaTratamiento) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SesionCreada(UUID sesionId, String tokenRenovacion, UUID usuarioId, String mensajeBienvenida,
            String insignia) {

        @Override
        public String toString() {
            return "SesionCreada[sesionId=" + sesionId + "]";
        }
    }

    public record RespuestaSesion(String tokenAcceso, String tokenRenovacion, long expiraEnSegundos,
            String mensajeBienvenida, String insignia) {

        @Override
        public String toString() {
            return "RespuestaSesion[...]";
        }
    }

    public record SolicitudIngreso(
            @NotNull(message = "Ingresa tu DNI") @Pattern(regexp = "\\d{8}", message = "El DNI debe tener 8 dígitos") String dni,
            @NotNull(message = "Elige SMS o WhatsApp") Canal canal) {

        @Override
        public String toString() {
            return "SolicitudIngreso[canal=" + canal + "]";
        }
    }

    public record SolicitudIngresoCelular(
            @NotBlank(message = "Ingresa tu número de celular") String celular,
            Canal canal) {
    }

    public record RespuestaIngresoCelular(
            String tokenIngreso,
            String celular,
            String celularFormateado,
            String celularEnmascarado,
            String canal,
            int venceEnSegundos,
            int reenvioEnSegundos,
            String devCode) {
    }

    public record SolicitudVerificarCelular(
            String tokenIngreso,
            String celular,
            @NotNull(message = "Ingresa el código") @Pattern(regexp = "\\d{4}", message = "El código tiene 4 dígitos") String codigo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResultadoIngreso(String tokenIngreso, String celularEnmascarado, int venceEnSegundos,
            int reenvioEnSegundos, EnvioPendiente envio) {

        @Override
        public String toString() {
            return "ResultadoIngreso[...]";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EnvioPendiente(String celular, String codigo, Canal canal, int minutosDeVigencia) {

        @Override
        public String toString() {
            return "EnvioPendiente[...]";
        }
    }

    public record RespuestaIngreso(String tokenIngreso, String celularEnmascarado, int venceEnSegundos,
            int reenvioEnSegundos) {
    }

    public record SolicitudVerificarIngreso(
            @NotBlank(message = "Falta el token de ingreso") String tokenIngreso,
            @NotNull(message = "Ingresa el código") @Pattern(regexp = "\\d{4}", message = "El código tiene 4 dígitos") String codigo) {

        @Override
        public String toString() {
            return "SolicitudVerificarIngreso[...]";
        }
    }

    public record RecuperacionPendiente(boolean pendiente, String aplicaEn, String mensaje) {
    }

    public record SolicitudRenovar(@NotBlank(message = "Falta el token de renovación") String tokenRenovacion) {

        @Override
        public String toString() {
            return "SolicitudRenovar[...]";
        }
    }
}
