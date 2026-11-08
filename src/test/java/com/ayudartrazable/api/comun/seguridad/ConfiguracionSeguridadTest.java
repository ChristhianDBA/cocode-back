package com.ayudartrazable.api.comun.seguridad;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = ConfiguracionSeguridadTest.ControladorPrueba.class)
@Import({ConfiguracionSeguridad.class, ConfiguracionSeguridadTest.ControladorPrueba.class})
@TestPropertySource(properties = "seguridad.jwt.secreto=" + ConfiguracionSeguridadTest.SECRETO)
class ConfiguracionSeguridadTest {

    static final String SECRETO = "secreto-de-pruebas-con-al-menos-32-bytes-de-largo";
    private static final UUID SESION = UUID.randomUUID();

    @RestController
    static class ControladorPrueba {
        @GetMapping("/api/v1/vecinos/me")
        String yo(@AuthenticationPrincipal UsuarioActual usuario) {
            return usuario.nombres();
        }

        @GetMapping("/api/v1/admin/avisos")
        String avisos() {
            return "ok";
        }

        @PostMapping("/api/v1/auth/dni")
        String publico() {
            return "publico";
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ValidadorSesion validador;

    @BeforeEach
    void preparar() {
        when(validador.validar(any())).thenReturn(Optional.empty());
        when(validador.validar(SESION)).thenReturn(Optional.of(usuario("VECINO")));
    }

    private static UsuarioActual usuario(String... roles) {
        return new UsuarioActual(UUID.randomUUID(), SESION, roles[0], "Ana María", "F", null, null, List.of(roles));
    }

    private static String bearer(String sesion, long segundos) {
        return "Bearer " + TokensDePrueba.firmar(SECRETO, sesion, segundos);
    }

    @Test
    void RNF04_rutaProtegidaSinToken_devuelve401ConApiError() throws Exception {
        mvc.perform(get("/api/v1/vecinos/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").value("Tu sesión no es válida. Inicia sesión nuevamente"));
    }

    @Test
    void RNF04_tokenFirmadoConOtraClave_devuelve401() throws Exception {
        String token = TokensDePrueba.firmar("otra-clave-distinta-con-al-menos-32-bytes!!", SESION.toString(), 300);
        mvc.perform(get("/api/v1/vecinos/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void RF07_tokenVencido_devuelve401() throws Exception {
        mvc.perform(get("/api/v1/vecinos/me").header("Authorization", bearer(SESION.toString(), -120)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void RF07_tokenValidoConSesionVigente_entregaElUsuario() throws Exception {
        mvc.perform(get("/api/v1/vecinos/me").header("Authorization", bearer(SESION.toString(), 300)))
                .andExpect(status().isOk())
                .andExpect(content().string("Ana María"));
    }

    @Test
    void RF07_tokenValidoPeroSesionRevocada_devuelve401() throws Exception {
        mvc.perform(get("/api/v1/vecinos/me").header("Authorization", bearer(UUID.randomUUID().toString(), 300)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void RNF04_tokenConSujetoNoUuid_devuelve401() throws Exception {
        mvc.perform(get("/api/v1/vecinos/me").header("Authorization", bearer("1", 300)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void RNF04_rutaPublica_noExigeToken() throws Exception {
        mvc.perform(post("/api/v1/auth/dni"))
                .andExpect(status().isOk())
                .andExpect(content().string("publico"));
    }

    @Test
    void RF16_rutaAdministrativaSinRol_devuelve403ConApiError() throws Exception {
        mvc.perform(get("/api/v1/admin/avisos").header("Authorization", bearer(SESION.toString(), 300)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"))
                .andExpect(jsonPath("$.mensaje").value("No tienes permiso para realizar esta acción"));
    }

    @Test
    void RF16_rutaAdministrativaConRolAdmin_permitida() throws Exception {
        when(validador.validar(SESION)).thenReturn(Optional.of(usuario("ADMIN")));
        mvc.perform(get("/api/v1/admin/avisos").header("Authorization", bearer(SESION.toString(), 300)))
                .andExpect(status().isOk());
    }
}
