package com.ayudartrazable.api.integraciones.mensajeria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ayudartrazable.api.comun.errores.ExcepcionNegocio;
import com.ayudartrazable.api.comun.errores.ExcepcionServicioNoDisponible;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class EnvioCodigoTest {

    private static final String URL = "https://twilio.test";
    private static final String MENSAJES = URL + "/2010-04-01/Accounts/AC123/Messages.json";
    private MockRestServiceServer servidor;
    private EnvioCodigoTwilio adaptador;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder().baseUrl(URL);
        servidor = MockRestServiceServer.bindTo(builder).build();
        adaptador = new EnvioCodigoTwilio(builder, "AC123", "clave", "+15550001111", "+15550002222");
    }

    @Test
    void RF03_envioPorSms_usaNumeroSmsYAutenticacionBasica() {
        String basica = Base64.getEncoder().encodeToString("AC123:clave".getBytes(StandardCharsets.UTF_8));
        servidor.expect(requestTo(MENSAJES))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic " + basica))
                .andExpect(content().formDataContains(java.util.Map.of(
                        "To", "+51987654321",
                        "From", "+15550001111",
                        "Body", "Tu código de Ayudar Trazable es 1234. Vence en 5 minutos.")))
                .andRespond(withSuccess("{\"sid\":\"SM1\"}", MediaType.APPLICATION_JSON));

        adaptador.enviar("987654321", "1234", Canal.SMS, 5);
        servidor.verify();
    }

    @Test
    void RF03_envioPorWhatsapp_usaPrefijoWhatsapp() {
        servidor.expect(requestTo(MENSAJES))
                .andExpect(content().formDataContains(java.util.Map.of(
                        "To", "whatsapp:+51987654321",
                        "From", "whatsapp:+15550002222")))
                .andRespond(withSuccess("{\"sid\":\"SM2\"}", MediaType.APPLICATION_JSON));

        adaptador.enviar("987654321", "1234", Canal.WHATSAPP, 5);
        servidor.verify();
    }

    @Test
    void RF19_avisoAlCelularAnterior_porSms() {
        servidor.expect(requestTo(MENSAJES))
                .andExpect(content().formDataContains(java.util.Map.of(
                        "To", "+51987654321", "From", "+15550001111", "Body", "aviso de prueba")))
                .andRespond(withSuccess("{\"sid\":\"SM3\"}", MediaType.APPLICATION_JSON));
        adaptador.avisar("987654321", "aviso de prueba");
        servidor.verify();
    }

    @Test
    void RF03_numeroRechazadoPorProveedor_esErrorDeNegocio() {
        servidor.expect(requestTo(MENSAJES)).andRespond(withBadRequest());
        assertThatThrownBy(() -> adaptador.enviar("987654321", "1234", Canal.SMS, 5))
                .isInstanceOf(ExcepcionNegocio.class)
                .hasMessage("No pudimos enviar el código a ese número. Revisa el celular ingresado");
    }

    @Test
    void RNF10_proveedorCaido_esServicioNoDisponible() {
        servidor.expect(requestTo(MENSAJES)).andRespond(withServerError());
        assertThatThrownBy(() -> adaptador.enviar("987654321", "1234", Canal.SMS, 5))
                .isInstanceOf(ExcepcionServicioNoDisponible.class)
                .hasMessage("No pudimos enviar el código en este momento. Intenta más tarde");
    }

    @Test
    void RF03_simulador_guardaElUltimoCodigoYCanalPorCelular() {
        EnvioCodigoSimulado simulado = new EnvioCodigoSimulado();
        simulado.enviar("987654321", "1111", Canal.SMS, 5);
        simulado.enviar("987654321", "2222", Canal.WHATSAPP, 5);
        assertThat(simulado.ultimo("987654321")).contains(new EnvioCodigoSimulado.Envio("2222", Canal.WHATSAPP));
        assertThat(simulado.ultimo("911111111")).isEmpty();
    }
}
