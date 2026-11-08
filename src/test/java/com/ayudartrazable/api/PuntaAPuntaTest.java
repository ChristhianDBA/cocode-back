package com.ayudartrazable.api;

import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_ANA;
import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_JUAN;
import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_LUIS;
import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_ROSA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ayudartrazable.api.soporte.Escenario;
import com.ayudartrazable.api.soporte.PruebaApi;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PuntaAPuntaTest extends PruebaApi {

    @LocalServerPort
    private int puerto;

    private BlockingQueue<Map<String, Object>> escuchar(Sesion sesion, String destino) throws Exception {
        WebSocketStompClient cliente = new WebSocketStompClient(new StandardWebSocketClient());
        cliente.setMessageConverter(new JacksonJsonMessageConverter());
        StompHeaders cabeceras = new StompHeaders();
        cabeceras.add("Authorization", sesion.bearer());
        StompSession s = cliente.connectAsync("ws://localhost:" + puerto + "/ws/emergencias", new WebSocketHttpHeaders(),
                cabeceras, new StompSessionHandlerAdapter() { }).get(5, TimeUnit.SECONDS);
        BlockingQueue<Map<String, Object>> cola = new LinkedBlockingQueue<>();
        s.subscribe(destino, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders h) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders h, Object carga) {
                cola.add((Map<String, Object>) carga);
            }
        });
        Thread.sleep(300);
        return cola;
    }

    private void dispositivo(Sesion s, String token) throws Exception {
        mvc.perform(put("/api/v1/vecinos/me/dispositivo").contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(Map.of("tokenPush", token))).header("Authorization", s.bearer())).andExpect(status().isOk());
    }

    @Test
    void T068_criteriosDeFinalizacionDeLaSpec() throws Exception {
        Sesion ana = registrar(DNI_ANA, "987654321", Escenario.SB04);
        Sesion juan = registrar(DNI_JUAN, "911111111", Escenario.SB04);
        Sesion rosa = registrar(DNI_ROSA, "922222222", Escenario.SB05);
        registrar(DNI_LUIS, "933333333", Escenario.SB04);
        dispositivo(juan, "tok-juan");
        dispositivo(rosa, "tok-rosa");

        mvc.perform(get("/api/v1/geo/cuadrantes/mio/vecinos").header("Authorization", ana.bearer()))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].latitud").doesNotExist());

        BlockingQueue<Map<String, Object>> enVivoJuan = escuchar(juan, "/topic/cuadrante/" + cuadrante("CUAD-SB-04"));
        BlockingQueue<Map<String, Object>> enVivoRosa = escuchar(rosa, "/topic/cuadrante/" + cuadrante("CUAD-SB-05"));

        JsonNode panico = leer(mvc.perform(postJson("/api/v1/emergencias/panico",
                Map.of("confirmado", true, "latitud", -12.0905, "longitud", -76.9902)).header("Authorization", ana.bearer()))
                .andExpect(status().isCreated()));
        String alertaId = panico.get("alerta").get("alertaId").asString();

        assertThat(enVivoJuan.poll(3, TimeUnit.SECONDS)).isNotNull();
        esperar(() -> !push.enviadosA("tok-juan").isEmpty());
        assertThat(enVivoRosa.poll(500, TimeUnit.MILLISECONDS)).isNull();
        assertThat(push.enviadosA("tok-rosa")).isEmpty();
        mvc.perform(get("/api/v1/emergencias/activas").header("Authorization", rosa.bearer()))
                .andExpect(jsonPath("$.length()").value(0));

        Sesion admin = administrador();
        mvc.perform(postJson("/api/v1/admin/coordinadores",
                        Map.of("usuarioId", idDeUsuario(DNI_LUIS), "cuadrantes", List.of(cuadrante("CUAD-SB-04"))))
                .header("Authorization", admin.bearer())).andExpect(status().isCreated());
        Sesion coordinador = ingresar(DNI_LUIS);

        String siguiente = alertaId;
        for (int i = 0; i < 3; i++) {
            mvc.perform(patch("/api/v1/emergencias/" + siguiente + "/estado").contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(Map.of("estado", "FALSA_ALARMA"))).header("Authorization", coordinador.bearer()))
                    .andExpect(status().isOk());
            if (i < 2) {
                siguiente = leer(mvc.perform(postJson("/api/v1/emergencias/panico", Map.of("confirmado", true))
                        .header("Authorization", ana.bearer())).andExpect(status().isCreated())).get("alerta").get("alertaId").asString();
            }
        }
        assertThat(this.admin.queryForObject("select count(*) from alertas_historial where alerta_id = ?::uuid", Integer.class, alertaId))
                .isEqualTo(2);
        mvc.perform(postJson("/api/v1/emergencias/panico", Map.of("confirmado", true)).header("Authorization", ana.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PANICO_SUSPENDIDO"));
    }
}
