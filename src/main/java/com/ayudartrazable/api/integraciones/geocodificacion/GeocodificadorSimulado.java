package com.ayudartrazable.api.integraciones.geocodificacion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.client.RestClient;

public class GeocodificadorSimulado implements Geocodificador {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaNominatim(String display_name, Map<String, String> address) {
    }

    private final RestClient http = RestClient.builder()
            .defaultHeader("User-Agent", "DeBarrioApp/1.0")
            .build();

    @Override
    public Optional<String> direccion(double latitud, double longitud) {
        try {
            RespuestaNominatim r = http.get()
                    .uri("https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat={lat}&lon={lon}&accept-language=es",
                            latitud, longitud)
                    .retrieve()
                    .body(RespuestaNominatim.class);
            if (r != null && r.address() != null) {
                String road = r.address().getOrDefault("road",
                        r.address().getOrDefault("pedestrian",
                                r.address().getOrDefault("neighbourhood", "")));
                String houseNumber = r.address().getOrDefault("house_number", "");
                String suburb = r.address().getOrDefault("suburb",
                        r.address().getOrDefault("neighbourhood", ""));
                String cityDistrict = r.address().getOrDefault("city_district",
                        r.address().getOrDefault("municipality",
                                r.address().getOrDefault("city",
                                        r.address().getOrDefault("town", "Lima"))));
                StringBuilder sb = new StringBuilder();
                if (!road.isBlank()) {
                    sb.append(road);
                    if (!houseNumber.isBlank()) sb.append(" ").append(houseNumber);
                } else if (!suburb.isBlank()) {
                    sb.append(suburb);
                }
                if (!suburb.isBlank() && !sb.toString().contains(suburb)) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(suburb);
                }
                if (!cityDistrict.isBlank()) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(cityDistrict);
                }
                if (sb.length() > 0) {
                    return Optional.of(sb.toString());
                }
            }
        } catch (Exception ignored) {
        }
        return Optional.of(String.format("Ubicación GPS (%.5f, %.5f)", latitud, longitud));
    }
}
