package com.ayudartrazable.api.notificaciones;

import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_ANA;
import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_JUAN;
import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_LUIS;
import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_ROSA;
import static org.assertj.core.api.Assertions.assertThat;

import com.ayudartrazable.api.soporte.Escenario;
import com.ayudartrazable.api.soporte.PruebaApi;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
class TiempoRealTest extends PruebaApi {

    @LocalServerPort
    private int puerto;

    private final List<StompSession> sesiones = new java.util.ArrayList<>();

    @AfterEach
    void cerrar() {
        sesiones.forEach(s -> {
            if (s.isConnected()) {
                s.disconnect();
            }
        });
    }

    private StompSession conectar(String bearer) throws Exception {
        WebSocketStompClient cliente = new WebSocketStompClient(new StandardWebSocketClient());
        cliente.setMessageConverter(new JacksonJsonMessageConverter());
        StompHeaders cabeceras = new StompHeaders();
        if (bearer != null) {
            cabeceras.add("Authorization", bearer);
        }
        StompSession sesion = cliente.connectAsync("ws://localhost:" + puerto + "/ws/emergencias",
                new WebSocketHttpHeaders(), cabeceras, new StompSessionHandlerAdapter() { }).get(5, TimeUnit.SECONDS);
        sesiones.add(sesion);
        return sesion;
    }

    private BlockingQueue<Map<String, Object>> suscribir(StompSession sesion, String destino) throws Exception {
        BlockingQueue<Map<String, Object>> cola = new LinkedBlockingQueue<>();
        sesion.subscribe(destino, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders cabeceras) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders cabeceras, Object carga) {
                cola.add((Map<String, Object>) carga);
            }
        });
        Thread.sleep(300);
        return cola;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapa(Object valor) {
        return (Map<String, Object>) valor;
    }

    private boolean conexionRechazada(String bearer) throws Exception {
        try {
            StompSession s = conectar(bearer);
            Thread.sleep(500);
            return !s.isConnected();
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            return true;
        }
    }

    @Test
    void RF13_conexionSinTokenOConTokenRevocado_rechazada() throws Exception {
        Sesion ana = registrar(DNI_ANA, "987654321", Escenario.SB04);
        assertThat(conexionRechazada(ana.bearer())).isFalse();
        assertThat(conexionRechazada(null)).isTrue();
        assertThat(conexionRechazada("Bearer token-falso")).isTrue();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/salir")
                .header("Authorization", ana.bearer()));
        assertThat(conexionRechazada(ana.bearer())).isTrue();
    }

    @Test
    void RF11_RF13_RNF01_alertaLlegaEnMenosDe3sSoloAlCuadranteYSinCoordenadas() throws Exception {
        Sesion ana = registrar(DNI_ANA, "987654321", Escenario.SB04);
        Sesion juan = registrar(DNI_JUAN, "911111111", Escenario.SB04);
        Sesion rosa = registrar(DNI_ROSA, "922222222", Escenario.SB05);
        UUID sb04 = cuadrante("CUAD-SB-04");
        UUID sb05 = cuadrante("CUAD-SB-05");

        BlockingQueue<Map<String, Object>> deJuan = suscribir(conectar(juan.bearer()), "/topic/cuadrante/" + sb04);
        BlockingQueue<Map<String, Object>> deRosa = suscribir(conectar(rosa.bearer()), "/topic/cuadrante/" + sb05);

        long inicio = System.nanoTime();
        mvc.perform(postJson("/api/v1/emergencias/panico", Map.of("confirmado", true, "latitud", -12.0905, "longitud", -76.9902))
                .header("Authorization", ana.bearer()));
        Map<String, Object> evento = deJuan.poll(3, TimeUnit.SECONDS);
        long milisegundos = (System.nanoTime() - inicio) / 1_000_000;

        assertThat(evento).isNotNull();
        assertThat(milisegundos).isLessThan(3000);
        assertThat(evento.get("evento")).isEqualTo("ALERTA_CREADA");
        @SuppressWarnings("unchecked")
        Map<String, Object> alerta = (Map<String, Object>) evento.get("alerta");
        assertThat(alerta).containsEntry("emisor", "Ana María Q.").containsEntry("referenciaCuadra", "Jr. Jacarandá cuadra 3")
                .containsKey("creadaEn").doesNotContainKey("latitud");
        assertThat(deRosa.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void A3_RF07_RF08_suscripcionVivaDejaDeRecibirTrasCerrarSesionOMudarse() throws Exception {
        Sesion ana = registrar(DNI_ANA, "987654321", Escenario.SB04);
        Sesion juan = registrar(DNI_JUAN, "911111111", Escenario.SB04);
        Sesion rosa = registrar(DNI_ROSA, "922222222", Escenario.SB04);
        Sesion luis = registrar(DNI_LUIS, "933333333", Escenario.SB04);
        String topico = "/topic/cuadrante/" + cuadrante("CUAD-SB-04");
        BlockingQueue<Map<String, Object>> deJuan = suscribir(conectar(juan.bearer()), topico);
        BlockingQueue<Map<String, Object>> deRosa = suscribir(conectar(rosa.bearer()), topico);
        BlockingQueue<Map<String, Object>> deLuis = suscribir(conectar(luis.bearer()), topico);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/salir")
                .header("Authorization", juan.bearer()));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/vecinos/me/domicilio")
                .contentType("application/json").content(cuerpo(Map.of("latitud", Escenario.SB05[0], "longitud", Escenario.SB05[1])))
                .header("Authorization", rosa.bearer()));

        mvc.perform(postJson("/api/v1/emergencias/panico", Map.of("confirmado", true)).header("Authorization", ana.bearer()));

        assertThat(deLuis.poll(3, TimeUnit.SECONDS)).isNotNull();
        assertThat(deJuan.poll(500, TimeUnit.MILLISECONDS)).isNull();
        assertThat(deRosa.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void RF13_suscripcionAOtroCuadrante_rechazada() throws Exception {
        Sesion ana = registrar(DNI_ANA, "987654321", Escenario.SB04);
        StompSession s = conectar(ana.bearer());
        suscribir(s, "/topic/cuadrante/" + cuadrante("CUAD-SB-05"));
        Thread.sleep(300);
        assertThat(s.isConnected()).isFalse();
    }

    @Test
    void RF15_RF16_serenazgoRecibeEnSuColaConUbicacionYCambiosDeEstado() throws Exception {
        Sesion ana = registrar(DNI_ANA, "987654321", Escenario.SB04);
        Sesion vecino = registrar(DNI_JUAN, "911111111", Escenario.SB04);
        Sesion admin = administrador();
        int sanBorja = this.admin.queryForObject("select id from distritos where nombre = 'San Borja'", Integer.class);
        mvc.perform(postJson("/api/v1/admin/serenazgo", Map.of("dni", DNI_LUIS, "celular", "955555555", "distritoId", sanBorja, "fechaNacimiento", "1980-01-01", "sexo", "M"))
                .header("Authorization", admin.bearer()));
        Sesion sereno = ingresar(DNI_LUIS);

        BlockingQueue<Map<String, Object>> cola = suscribir(conectar(sereno.bearer()), "/user/queue/alertas");
        BlockingQueue<Map<String, Object>> barrio = suscribir(conectar(vecino.bearer()), "/topic/cuadrante/" + cuadrante("CUAD-SB-04"));

        JsonNode p = leer(mvc.perform(postJson("/api/v1/emergencias/panico", Map.of("confirmado", true, "latitud", -12.0905, "longitud", -76.9902))
                .header("Authorization", ana.bearer())));
        Map<String, Object> creada = cola.poll(3, TimeUnit.SECONDS);
        assertThat(creada).isNotNull();
        assertThat(mapa(creada.get("alerta"))).containsKey("latitud");
        assertThat(barrio.poll(3, TimeUnit.SECONDS)).isNotNull();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/v1/emergencias/" + p.get("alerta").get("alertaId").asString() + "/estado")
                .contentType("application/json").content("{\"estado\":\"ATENDIDA\"}").header("Authorization", sereno.bearer()));
        Map<String, Object> actualizada = barrio.poll(3, TimeUnit.SECONDS);
        assertThat(actualizada).isNotNull();
        assertThat(actualizada.get("evento")).isEqualTo("ALERTA_ACTUALIZADA");
        assertThat(mapa(actualizada.get("alerta"))).containsEntry("estado", "ATENDIDA").doesNotContainKey("latitud");
    }
}
