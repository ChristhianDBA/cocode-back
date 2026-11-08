package com.ayudartrazable.api.integraciones.geocodificacion;

import java.util.Optional;

public class GeocodificadorSimulado implements Geocodificador {

    @Override
    public Optional<String> direccion(double latitud, double longitud) {
        return Optional.of("Jr. Jacarandá 342, San Borja");
    }
}
