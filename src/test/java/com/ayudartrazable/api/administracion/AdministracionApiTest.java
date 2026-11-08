package com.ayudartrazable.api.administracion;

import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_ANA;
import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_JUAN;
import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_LUIS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ayudartrazable.api.soporte.Escenario;
import com.ayudartrazable.api.soporte.PruebaApi;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class AdministracionApiTest extends PruebaApi {

    @Test
    void RF16_vecinoNoAccedeAAdministracion() throws Exception {
        Sesion ana = registrar(DNI_ANA, "987654321", Escenario.SB04);
        mvc.perform(get("/api/v1/admin/avisos").header("Authorization", ana.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
    }

    @Test
    void RF16_adminBuscaYDesignaCoordinador_rolRetiradoSeAplicaEnLaSiguienteSolicitud() throws Exception {
        registrar(DNI_JUAN, "911111111", Escenario.SB04);
        Sesion juan = ingresar(DNI_JUAN);
        Sesion admin = administrador();
        mvc.perform(get("/api/v1/admin/usuarios").param("dni", DNI_JUAN).header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Juan Carlos P."));
        mvc.perform(postJson("/api/v1/admin/coordinadores",
                        Map.of("usuarioId", idDeUsuario(DNI_JUAN), "cuadrantes", List.of(cuadrante("CUAD-SB-04"), cuadrante("CUAD-SB-05"))))
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.asignados").value(2));
        mvc.perform(get("/api/v1/emergencias/activas").header("Authorization", juan.bearer())).andExpect(status().isOk());
        mvc.perform(postJson("/api/v1/admin/coordinadores/" + idDeUsuario(DNI_JUAN) + "/retirar", Map.of())
                .header("Authorization", admin.bearer())).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/emergencias/00000000-0000-0000-0000-000000000000/estado")
                        .contentType("application/json").content("{\"estado\":\"RESUELTA\"}")
                        .header("Authorization", juan.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
    }

    @Test
    void RF16_CL16_serenazgoAltaIngresoYDesactivacion() throws Exception {
        Sesion admin = administrador();
        int sanBorja = this.admin.queryForObject("select id from distritos where nombre = 'San Borja'", Integer.class);
        JsonNode alta = leer(mvc.perform(postJson("/api/v1/admin/serenazgo", Map.of("dni", DNI_LUIS, "celular", "955555555", "distritoId", sanBorja, "fechaNacimiento", "1980-01-01", "sexo", "M"))
                .header("Authorization", admin.bearer())).andExpect(status().isCreated()));
        mvc.perform(postJson("/api/v1/admin/serenazgo", Map.of("dni", "00000000", "celular", "966666666", "distritoId", sanBorja, "fechaNacimiento", "1980-01-01", "sexo", "M"))
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isNotFound());
        Sesion sereno = ingresar(DNI_LUIS);
        mvc.perform(get("/api/v1/emergencias/activas").header("Authorization", sereno.bearer())).andExpect(status().isOk());
        mvc.perform(postJson("/api/v1/admin/serenazgo/" + alta.get("usuarioId").asString() + "/desactivar", Map.of())
                .header("Authorization", admin.bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/emergencias/activas").header("Authorization", sereno.bearer()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void RF21_deshabilitarYHabilitarAvisaPorPush() throws Exception {
        Sesion ana = registrar(DNI_ANA, "987654321", Escenario.SB04);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/vecinos/me/dispositivo")
                .contentType("application/json").content("{\"tokenPush\":\"tok-ana\"}").header("Authorization", ana.bearer()))
                .andExpect(status().isOk());
        Sesion juan = registrar(DNI_JUAN, "911111111", new double[] {-12.0905, -76.9860});
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/vecinos/me/dispositivo")
                .contentType("application/json").content("{\"tokenPush\":\"tok-juan\"}").header("Authorization", juan.bearer()))
                .andExpect(status().isOk());
        Sesion admin = administrador();
        String deshabilitado = mvc.perform(post("/api/v1/admin/cuadrantes/" + cuadrante("CUAD-SB-04") + "/deshabilitar").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinCuadrante.length()").value(1))
                .andExpect(jsonPath("$.reasignados.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(deshabilitado).doesNotContain("tok-ana").doesNotContain("tok-juan");
        esperar(() -> push.enviadosA("tok-ana").stream().anyMatch(n -> n.titulo().equals("Tu zona aún no tiene cobertura")));
        esperar(() -> push.enviadosA("tok-juan").stream().anyMatch(n -> n.titulo().equals("Cambió tu cuadrante")));
        String habilitado = mvc.perform(post("/api/v1/admin/cuadrantes/" + cuadrante("CUAD-SB-04") + "/habilitar").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(habilitado).doesNotContain("tok-ana");
        esperar(() -> push.enviadosA("tok-ana").stream().anyMatch(n -> n.titulo().equals("Tu zona ya tiene cobertura")));
        mvc.perform(get("/api/v1/geo/cuadrantes/mio").header("Authorization", ana.bearer())).andExpect(status().isOk());
    }

    private String tramiteRecuperacion(String dni, String celularNuevo) throws Exception {
        String tramite = leer(mvc.perform(postJson("/api/v1/auth/recuperacion/dni", Map.of("dni", dni, "origen", "MANUAL", "fechaNacimiento", "1990-01-01", "sexo", "F")))
                .andExpect(status().isCreated())).get("tokenTramite").asString();
        mvc.perform(postJson("/api/v1/auth/codigo", Map.of("celular", celularNuevo, "canal", "WHATSAPP")).header("X-Tramite", tramite))
                .andExpect(status().isOk());
        mvc.perform(postJson("/api/v1/auth/codigo/verificar", Map.of("codigo", "1234")).header("X-Tramite", tramite))
                .andExpect(status().isOk());
        return tramite;
    }

    @Test
    void T073_RF19_solicitudQuedaEnEsperaYAvisaAlCelularAnterior() throws Exception {
        Sesion vieja = registrar(DNI_ANA, "987654321", Escenario.SB04);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/vecinos/me/dispositivo")
                .contentType("application/json").content("{\"tokenPush\":\"tok-ana\"}").header("Authorization", vieja.bearer()))
                .andExpect(status().isOk());
        String tramite = tramiteRecuperacion(DNI_ANA, "944444444");
        String respuesta = mvc.perform(post("/api/v1/auth/recuperacion/completar").header("X-Tramite", tramite))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.pendiente").value(true))
                .andExpect(jsonPath("$.aplicaEn").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(respuesta).doesNotContain("987654321").doesNotContain("tok-ana").doesNotContain("usuarioId");
        assertThat(mensajes.avisosA("987654321")).singleElement().asString().contains("cancela la recuperación");
        esperar(() -> !push.enviadosA("tok-ana").isEmpty());
        mvc.perform(get("/api/v1/vecinos/me").header("Authorization", vieja.bearer())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/recuperacion/completar").header("X-Tramite", tramite))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RECUPERACION_EN_ESPERA"));
    }

    @Test
    void T072_RF19_titularCancelaYElSolicitanteNoPuedeCompletar() throws Exception {
        Sesion titular = registrar(DNI_ANA, "987654321", Escenario.SB04);
        String tramite = tramiteRecuperacion(DNI_ANA, "944444444");
        mvc.perform(post("/api/v1/auth/recuperacion/completar").header("X-Tramite", tramite)).andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/vecinos/me/recuperacion/cancelar").header("Authorization", titular.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canceladas").value(1));
        admin.update("update altas_en_curso set aplica_en = now() - interval '1 second' where proposito = 'RECUPERACION'");
        mvc.perform(post("/api/v1/auth/recuperacion/completar").header("X-Tramite", tramite))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("El titular de la cuenta canceló la recuperación"));
    }

    @Test
    void T073_avisoFallido_cancelaLaRecuperacion() throws Exception {
        registrar(DNI_ANA, "987654321", Escenario.SB04);
        String tramite = tramiteRecuperacion(DNI_ANA, "944444444");
        mensajes.fallarProximo();
        mvc.perform(post("/api/v1/auth/recuperacion/completar").header("X-Tramite", tramite))
                .andExpect(status().isServiceUnavailable());
        admin.update("update altas_en_curso set aplica_en = now() - interval '1 second' where proposito = 'RECUPERACION'");
        mvc.perform(post("/api/v1/auth/recuperacion/completar").header("X-Tramite", tramite))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RECUPERACION_CANCELADA"));
    }

    @Test
    void RF19_recuperacionPasoAPaso() throws Exception {
        Sesion vieja = registrar(DNI_ANA, "987654321", Escenario.SB04);
        String tramite = tramiteRecuperacion(DNI_ANA, "944444444");
        mvc.perform(post("/api/v1/auth/recuperacion/completar").header("X-Tramite", tramite))
                .andExpect(status().isAccepted());
        admin.update("update altas_en_curso set aplica_en = now() - interval '1 second' where proposito = 'RECUPERACION'");
        mvc.perform(post("/api/v1/auth/recuperacion/completar").header("X-Tramite", tramite))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenAcceso").isNotEmpty());
        mvc.perform(get("/api/v1/vecinos/me").header("Authorization", vieja.bearer())).andExpect(status().isUnauthorized());
        mensajes.limpiar();
        ingresar(DNI_ANA);
        assertThat(mensajes.ultimo("944444444")).isPresent();
        assertThat(mensajes.ultimo("987654321")).isEmpty();
    }
}
