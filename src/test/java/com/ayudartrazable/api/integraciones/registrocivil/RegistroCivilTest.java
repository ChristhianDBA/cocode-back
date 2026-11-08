package com.ayudartrazable.api.integraciones.registrocivil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ayudartrazable.api.comun.errores.ExcepcionServicioNoDisponible;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RegistroCivilTest {

    private static final String PLANTILLA = "https://registro.test/v1/reniec/dni?numero={dni}";
    private MockRestServiceServer servidor;
    private RegistroCivilReniec adaptador;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        adaptador = new RegistroCivilReniec(builder, PLANTILLA, "token-prueba");
    }

    @Test
    void RF01_dniValido_devuelveNombresDelFormatoDecolecta() {
        servidor.expect(requestTo("https://registro.test/v1/reniec/dni?numero=46027897"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token-prueba"))
                .andRespond(withSuccess("""
                        {"first_name":"ROXANA KARINA","first_last_name":"DELGADO","second_last_name":"HUAMANI",
                         "full_name":"DELGADO HUAMANI ROXANA KARINA","document_number":"46027897"}
                        """, MediaType.APPLICATION_JSON));

        DatosCiviles datos = adaptador.consultar("46027897").orElseThrow();

        assertThat(datos).isEqualTo(new DatosCiviles("46027897", "ROXANA KARINA", "DELGADO", "HUAMANI", null, null));
        servidor.verify();
    }

    @Test
    void RF01_dniInexistente_devuelveVacio() {
        servidor.expect(requestTo("https://registro.test/v1/reniec/dni?numero=11111111"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body("{\"message\":\"not found\"}"));
        assertThat(adaptador.consultar("11111111")).isEmpty();
    }

    @Test
    void CL01_tokenInvalidoOCuotaAgotada_esServicioNoDisponible() {
        servidor.expect(requestTo("https://registro.test/v1/reniec/dni?numero=46027897"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"error\":\"Apikey Required / Limit Exceeded\"}"));
        assertThatThrownBy(() -> adaptador.consultar("46027897"))
                .isInstanceOf(ExcepcionServicioNoDisponible.class)
                .hasMessage("No pudimos validar tu DNI en este momento. Intenta más tarde");
    }

    @Test
    void CL01_servicioConError_lanzaServicioNoDisponible() {
        servidor.expect(requestTo("https://registro.test/v1/reniec/dni?numero=46027897")).andRespond(withServerError());
        assertThatThrownBy(() -> adaptador.consultar("46027897"))
                .isInstanceOf(ExcepcionServicioNoDisponible.class);
    }

    @Test
    void CL01_servicioSinRespuesta_lanzaServicioNoDisponible() {
        servidor.expect(requestTo("https://registro.test/v1/reniec/dni?numero=46027897"))
                .andRespond(withException(new SocketTimeoutException()));
        assertThatThrownBy(() -> adaptador.consultar("46027897"))
                .isInstanceOf(ExcepcionServicioNoDisponible.class);
    }

    @Test
    void RF02_simulador_devuelveDatosDeDnisDePrueba() {
        RegistroCivilSimulado simulado = new RegistroCivilSimulado();
        assertThat(simulado.consultar("45678912")).get()
                .extracting(DatosCiviles::nombres).isEqualTo("Ana María");
        assertThat(simulado.consultar("00000000")).isEmpty();
    }

    @Test
    void CL14_simulador_tieneUnMenorDeEdadQueCumple18Manana() {
        DatosCiviles menor = new RegistroCivilSimulado().consultar(RegistroCivilSimulado.DNI_MENOR).orElseThrow();
        assertThat(menor.fechaNacimiento()).isEqualTo(LocalDate.now().minusYears(18).plusDays(1));
    }
}
