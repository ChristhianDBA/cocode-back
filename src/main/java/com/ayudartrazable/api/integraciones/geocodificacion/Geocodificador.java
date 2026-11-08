package com.ayudartrazable.api.integraciones.geocodificacion;

import java.util.Optional;

public interface Geocodificador {

    Optional<String> direccion(double latitud, double longitud);
}
