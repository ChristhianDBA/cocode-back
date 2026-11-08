package com.ayudartrazable.api.integraciones.geocodificacion;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class GeocodificadorGoogle implements Geocodificador {

    private static final Logger LOG = LoggerFactory.getLogger(GeocodificadorGoogle.class);

    private final RestClient cliente;
    private final String clave;

    public GeocodificadorGoogle(RestClient.Builder builder, String clave) {
        this.cliente = builder.build();
        this.clave = clave;
    }

    record Resultado(String formatted_address) {
    }

    record Respuesta(String status, List<Resultado> results) {
    }

    @Override
    public Optional<String> direccion(double latitud, double longitud) {
        try {
            Respuesta r = cliente.get()
                    .uri(u -> u.path("/maps/api/geocode/json")
                            .queryParam("latlng", String.format(Locale.ROOT, "%s,%s", latitud, longitud))
                            .queryParam("language", "es")
                            .queryParam("key", clave)
                            .build())
                    .retrieve()
                    .body(Respuesta.class);
            if (r == null || !"OK".equals(r.status()) || r.results() == null || r.results().isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(r.results().getFirst().formatted_address());
        } catch (RestClientException e) {
            LOG.warn("Geocodificación no disponible: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
