package com.ayudartrazable.api.comun.errores;

public class ExcepcionServicioNoDisponible extends RuntimeException {

    public ExcepcionServicioNoDisponible(String mensaje) {
        super(mensaje);
    }

    public ExcepcionServicioNoDisponible(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
