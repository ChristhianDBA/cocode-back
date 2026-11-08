package com.ayudartrazable.api.integraciones.registrocivil;

import java.time.LocalDate;

public record DatosCiviles(String dni, String nombres, String apellidoPaterno, String apellidoMaterno,
        LocalDate fechaNacimiento, String sexo) {

    public DatosCiviles completarCon(LocalDate fechaDelDocumento, String sexoDelDocumento) {
        return new DatosCiviles(dni, nombres, apellidoPaterno, apellidoMaterno,
                fechaNacimiento != null ? fechaNacimiento : fechaDelDocumento,
                sexo != null ? sexo : sexoDelDocumento);
    }

    @Override
    public String toString() {
        return "DatosCiviles[dni=******" + dni.substring(Math.max(0, dni.length() - 2)) + "]";
    }
}
