package com.ayudartrazable.api.integraciones.registrocivil;

import com.ayudartrazable.api.comun.errores.ExcepcionServicioNoDisponible;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class RegistroCivilReniec implements ConsultaRegistroCivil {

    private static final Logger LOG = LoggerFactory.getLogger(RegistroCivilReniec.class);
    static final String NO_DISPONIBLE = "No pudimos validar tu DNI en este momento. Intenta más tarde";

    private final RestClient cliente;
    private final String plantilla;

    public RegistroCivilReniec(RestClient.Builder builder, String plantilla, String token) {
        this.cliente = builder.defaultHeader("Authorization", "Bearer " + token).build();
        this.plantilla = plantilla;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Respuesta(@JsonProperty("document_number") String numero, @JsonProperty("first_name") String nombres,
            @JsonProperty("first_last_name") String apellidoPaterno,
            @JsonProperty("second_last_name") String apellidoMaterno) {
    }

    @Override
    public Optional<DatosCiviles> consultar(String dni) {
        try {
            Respuesta r = cliente.get().uri(plantilla, dni).retrieve().body(Respuesta.class);
            if (r == null || r.nombres() == null) {
                throw new ExcepcionServicioNoDisponible(NO_DISPONIBLE);
            }
            return Optional.of(new DatosCiviles(r.numero(), r.nombres(), r.apellidoPaterno(), r.apellidoMaterno(),
                    null, null));
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND || e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                return Optional.empty();
            }
            LOG.warn("Registro civil rechazó la consulta: estado {}", e.getStatusCode().value());
            throw new ExcepcionServicioNoDisponible(NO_DISPONIBLE, e);
        } catch (RestClientException e) {
            throw new ExcepcionServicioNoDisponible(NO_DISPONIBLE, e);
        }
    }
}
