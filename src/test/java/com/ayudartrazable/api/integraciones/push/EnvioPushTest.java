package com.ayudartrazable.api.integraciones.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class EnvioPushTest {

    private static final String URL = "https://fcm.test";
    private static final String ENVIO = URL + "/v1/projects/ayudar/messages:send";
    private static final NotificacionPush AVISO = new NotificacionPush("Alerta de pánico",
            "Ana María Q. · Jr. Jacarandá cuadra 3", Map.of("alertaId", "a1", "tipo", "PANICO"));
    private MockRestServiceServer servidor;
    private EnvioPushFcm adaptador;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder().baseUrl(URL);
        servidor = MockRestServiceServer.bindTo(builder).build();
        adaptador = new EnvioPushFcm(builder, "ayudar", () -> "acceso-google");
    }

    @Test
    void RF13_envioExitoso_mandaMensajeDeAltaPrioridad() {
        servidor.expect(requestTo(ENVIO))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer acceso-google"))
                .andExpect(jsonPath("$.message.token").value("tok-1"))
                .andExpect(jsonPath("$.message.notification.title").value("Alerta de pánico"))
                .andExpect(jsonPath("$.message.data.alertaId").value("a1"))
                .andExpect(jsonPath("$.message.android.priority").value("high"))
                .andRespond(withSuccess("{\"name\":\"projects/ayudar/messages/1\"}", MediaType.APPLICATION_JSON));

        assertThat(adaptador.enviar("tok-1", AVISO)).isEqualTo(ResultadoPush.ENVIADO);
        servidor.verify();
    }

    @Test
    void RF14_tokenNoRegistrado_esTokenInvalido() {
        servidor.expect(requestTo(ENVIO)).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"status\":\"NOT_FOUND\",\"details\":[{\"errorCode\":\"UNREGISTERED\"}]}}"));
        assertThat(adaptador.enviar("tok-viejo", AVISO)).isEqualTo(ResultadoPush.TOKEN_INVALIDO);
    }

    @Test
    void RF14_tokenMalFormado_esTokenInvalido() {
        servidor.expect(requestTo(ENVIO)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"status\":\"INVALID_ARGUMENT\",\"details\":[{\"errorCode\":\"INVALID_ARGUMENT\"}]}}"));
        assertThat(adaptador.enviar("xx", AVISO)).isEqualTo(ResultadoPush.TOKEN_INVALIDO);
    }

    @Test
    void RF14_fallasDelServicio_sonErrorTemporal() {
        servidor.expect(requestTo(ENVIO)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(adaptador.enviar("tok-1", AVISO)).isEqualTo(ResultadoPush.ERROR_TEMPORAL);
    }

    @Test
    void RF14_sinRed_esErrorTemporal() {
        servidor.expect(requestTo(ENVIO)).andRespond(withException(new SocketTimeoutException()));
        assertThat(adaptador.enviar("tok-1", AVISO)).isEqualTo(ResultadoPush.ERROR_TEMPORAL);
    }

    @Test
    void RF14_simulador_rechazaTokensMarcadosInvalidosYRegistraEnvios() {
        EnvioPushSimulado simulado = new EnvioPushSimulado();
        simulado.marcarInvalido("tok-viejo");
        assertThat(simulado.enviar("tok-viejo", AVISO)).isEqualTo(ResultadoPush.TOKEN_INVALIDO);
        assertThat(simulado.enviar("tok-1", AVISO)).isEqualTo(ResultadoPush.ENVIADO);
        assertThat(simulado.enviadosA("tok-1")).containsExactly(AVISO);
    }
}
