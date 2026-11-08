package com.ayudartrazable.api.emergencias;

import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_ANA;
import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_JUAN;
import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_ROSA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ayudartrazable.api.soporte.Escenario;
import com.ayudartrazable.api.soporte.PruebaApi;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

class EmergenciasApiTest extends PruebaApi {

    private void dispositivo(Sesion s, String token) throws Exception {
        mvc.perform(put("/api/v1/vecinos/me/dispositivo").contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(Map.of("tokenPush", token))).header("Authorization", s.bearer())).andExpect(status().isOk());
    }

    private void coordinador(Sesion admin, String dni, String codigoCuadrante) throws Exception {
        mvc.perform(postJson("/api/v1/admin/coordinadores",
                        Map.of("usuarioId", idDeUsuario(dni), "cuadrantes", List.of(cuadrante(codigoCuadrante))))
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isCreated());
    }

    @Test
    void RF11_RF13_panicoExitosoNotificaPushAVecinosDelCuadrante() throws Exception {
        Sesion ana = registrar(DNI_ANA, "987654321", Escenario.SB04);
        Sesion juan = registrar(DNI_JUAN, "911111111", Escenario.SB04);
        Sesion rosa = registrar(DNI_ROSA, "922222222", Escenario.SB05);
        dispositivo(juan, "tok-juan");
        dispositivo(rosa, "tok-rosa");

        mvc.perform(postJson("/api/v1/emergencias/panico", Map.of("confirmado", true, "latitud", -12.0905, "longitud", -76.9902))
                        .header("Authorization", ana.bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creada").value(true))
                .andExpect(jsonPath("$.alerta.emisor").value("Ana María Q."))
                .andExpect(jsonPath("$.alerta.latitud").doesNotExist())
                .andExpect(jsonPath("$.telefonos").doesNotExist());
        esperar(() -> !push.enviadosA("tok-juan").isEmpty());
        assertThat(push.enviadosA("tok-juan").getFirst().titulo()).isEqualTo("Alerta de pánico");
        assertThat(push.enviadosA("tok-rosa")).isEmpty();

        mvc.perform(postJson("/api/v1/emergencias/panico", Map.of("confirmado", true)).header("Authorization", ana.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creada").value(false));
    }

    @Test
    void RF11_sinConfirmacionYFueraDeCobertura() throws Exception {
        Sesion ana = registrar(DNI_ANA, "987654321", Escenario.SB04);
        mvc.perform(postJson("/api/v1/emergencias/panico", Map.of("confirmado", false)).header("Authorization", ana.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CONFIRMACION_REQUERIDA"));
        mvc.perform(postJson("/api/v1/emergencias/panico", Map.of("confirmado", true, "latitud", -12.0, "longitud", -77.1))
                        .header("Authorization", ana.bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fueraDeCobertura").value(true))
                .andExpect(jsonPath("$.telefonos.telefonos[0].numero").exists());
    }

    @Test
    void RF14_tokenRechazadoPorFcm_quedaInvalidado() throws Exception {
        Sesion ana = registrar(DNI_ANA, "987654321", Escenario.SB04);
        Sesion juan = registrar(DNI_JUAN, "911111111", Escenario.SB04);
        dispositivo(juan, "tok-viejo");
        push.marcarInvalido("tok-viejo");
        mvc.perform(postJson("/api/v1/emergencias/panico", Map.of("confirmado", true)).header("Authorization", ana.bearer()))
                .andExpect(status().isCreated());
        esperar(() -> admin.queryForObject("select count(*) from dispositivos where valido", Integer.class) == 0);
    }

    @Test
    void RF12_reportesValidacionYLimite() throws Exception {
        Sesion ana = registrar(DNI_ANA, "987654321", Escenario.SB04);
        mvc.perform(postJson("/api/v1/emergencias/reportes", Map.of("tipo", "INCENDIO", "descripcion", "x"))
                        .header("Authorization", ana.bearer()))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/v1/emergencias/reportes", Map.of("descripcion", "x")).header("Authorization", ana.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].campo").value("tipo"));
        for (int i = 0; i < 5; i++) {
            mvc.perform(postJson("/api/v1/emergencias/reportes", Map.of("tipo", "ALUMBRADO", "descripcion", "Poste " + i))
                    .header("Authorization", ana.bearer())).andExpect(status().isCreated());
        }
        mvc.perform(postJson("/api/v1/emergencias/reportes", Map.of("tipo", "ROBO", "descripcion", "x"))
                        .header("Authorization", ana.bearer()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("LIMITE_REPORTES"));
    }

    @Test
    void RF15_RF17_coordinadorGestionaYSuspendeTrasTresFalsasAlarmas() throws Exception {
        Sesion ana = registrar(DNI_ANA, "987654321", Escenario.SB04);
        registrar(DNI_JUAN, "911111111", Escenario.SB04);
        Sesion admin = administrador();
        coordinador(admin, DNI_JUAN, "CUAD-SB-04");
        Sesion juan = ingresar(DNI_JUAN);

        for (int i = 0; i < 3; i++) {
            JsonNode p = leer(mvc.perform(postJson("/api/v1/emergencias/panico", Map.of("confirmado", true))
                    .header("Authorization", ana.bearer())).andExpect(status().isCreated()));
            mvc.perform(patch("/api/v1/emergencias/" + p.get("alerta").get("alertaId").asString() + "/estado")
                            .contentType(MediaType.APPLICATION_JSON).content(cuerpo(Map.of("estado", "FALSA_ALARMA")))
                            .header("Authorization", juan.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("FALSA_ALARMA"))
                    .andExpect(jsonPath("$.latitud").doesNotExist());
        }
        mvc.perform(postJson("/api/v1/emergencias/panico", Map.of("confirmado", true)).header("Authorization", ana.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PANICO_SUSPENDIDO"));
        JsonNode avisos = leer(mvc.perform(get("/api/v1/admin/avisos").header("Authorization", admin.bearer()))
                .andExpect(status().isOk()));
        assertThat(avisos.toString()).contains("SUSPENSION");
        String suspension = admin_suspension();
        mvc.perform(postJson("/api/v1/admin/suspensiones/" + suspension + "/levantar", Map.of()).header("Authorization", admin.bearer()))
                .andExpect(status().isOk());
        mvc.perform(postJson("/api/v1/emergencias/panico", Map.of("confirmado", true)).header("Authorization", ana.bearer()))
                .andExpect(status().isCreated());
    }

    private String admin_suspension() {
        return admin.queryForObject("select id::text from suspensiones_panico where levantada_en is null", String.class);
    }

    @Test
    void RF15_vecinoComunNoPuedeCambiarEstado() throws Exception {
        Sesion ana = registrar(DNI_ANA, "987654321", Escenario.SB04);
        Sesion juan = registrar(DNI_JUAN, "911111111", Escenario.SB04);
        JsonNode p = leer(mvc.perform(postJson("/api/v1/emergencias/panico", Map.of("confirmado", true))
                .header("Authorization", ana.bearer())));
        mvc.perform(patch("/api/v1/emergencias/" + p.get("alerta").get("alertaId").asString() + "/estado")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo(Map.of("estado", "RESUELTA")))
                        .header("Authorization", juan.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
    }
}
