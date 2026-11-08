package com.ayudartrazable.api.integraciones.geocodificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeocodificadorTest {

    private static final String URL = "https://mapas.test";
    private MockRestServiceServer servidor;
    private GeocodificadorGoogle adaptador;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder().baseUrl(URL);
        servidor = MockRestServiceServer.bindTo(builder).build();
        adaptador = new GeocodificadorGoogle(builder, "clave-mapas");
    }

    @Test
    void RF04_direccionEstimada_tomaElPrimerResultado() {
        servidor.expect(requestTo(startsWith(URL + "/maps/api/geocode/json")))
                .andExpect(queryParam("latlng", "-12.0905,-76.9902"))
                .andExpect(queryParam("language", "es"))
                .andExpect(queryParam("key", "clave-mapas"))
                .andRespond(withSuccess("""
                        {"status":"OK","results":[{"formatted_address":"Jr. Jacarandá 342, San Borja 15037, Perú"},
                         {"formatted_address":"San Borja, Lima"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(adaptador.direccion(-12.0905, -76.9902)).contains("Jr. Jacarandá 342, San Borja 15037, Perú");
        servidor.verify();
    }

    @Test
    void RF04_sinResultados_devuelveVacio() {
        servidor.expect(requestTo(startsWith(URL)))
                .andRespond(withSuccess("{\"status\":\"ZERO_RESULTS\",\"results\":[]}", MediaType.APPLICATION_JSON));
        assertThat(adaptador.direccion(-12.0905, -76.9902)).isEmpty();
    }

    @Test
    void RNF10_proveedorCaido_noBloqueaElFlujoYDevuelveVacio() {
        servidor.expect(requestTo(startsWith(URL))).andRespond(withServerError());
        assertThat(adaptador.direccion(-12.0905, -76.9902)).isEmpty();
    }

    @Test
    void RF04_simulador_devuelveDireccionDePrueba() {
        assertThat(new GeocodificadorSimulado().direccion(-12.0905, -76.9902))
                .contains("Jr. Jacarandá 342, San Borja");
    }
}
