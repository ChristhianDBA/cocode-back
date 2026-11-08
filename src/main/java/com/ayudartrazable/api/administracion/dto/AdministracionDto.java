package com.ayudartrazable.api.administracion.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;

public final class AdministracionDto {

    private AdministracionDto() {
    }

    public record SolicitudCoordinador(
            @NotNull(message = "Indica el vecino") UUID usuarioId,
            @NotEmpty(message = "Indica al menos un cuadrante") List<UUID> cuadrantes) {
    }

    public record SolicitudRetiro(UUID cuadranteId) {
    }

    public record SolicitudSerenazgo(
            @NotNull(message = "Ingresa el DNI del responsable") @Pattern(regexp = "\\d{8}", message = "El DNI debe tener 8 dígitos") String dni,
            @NotNull(message = "Ingresa el celular del responsable") @Pattern(regexp = "9\\d{8}", message = "El celular debe tener 9 dígitos y empezar con 9") String celular,
            @NotNull(message = "Indica el distrito") Integer distritoId,
            @NotNull(message = "Ingresa la fecha de nacimiento del responsable") @Past(message = "La fecha de nacimiento no puede ser futura") java.time.LocalDate fechaNacimiento,
            @NotNull(message = "Indica el sexo del responsable") @Pattern(regexp = "[FM]", message = "El sexo debe ser F o M") String sexo) {

        @Override
        public String toString() {
            return "SolicitudSerenazgo[distritoId=" + distritoId + "]";
        }
    }

}
