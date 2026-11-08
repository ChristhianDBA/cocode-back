package com.ayudartrazable.api.soporte;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ayudartrazable.api.integraciones.mensajeria.EnvioCodigoSimulado;
import com.ayudartrazable.api.integraciones.push.EnvioPushSimulado;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ExtendWith(ExtensionBaseDeDatos.class)
public abstract class PruebaApi {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JsonMapper json;

    @Autowired
    protected EnvioCodigoSimulado mensajes;

    @Autowired
    protected EnvioPushSimulado push;

    @Autowired
    private JdbcTemplate jdbcDeLaApp;

    protected final JdbcTemplate admin = BaseDeDatosDePrueba.admin();

    @BeforeEach
    void prepararApi() {
        String base = jdbcDeLaApp.queryForObject("select current_database()", String.class);
        if (!BaseDeDatosDePrueba.BASE_PERMITIDA.equals(base)) {
            throw new IllegalStateException("La aplicación de pruebas apunta a " + base);
        }
        mensajes.limpiar();
        push.limpiar();
    }

    protected String cuerpo(Object valor) {
        return json.writeValueAsString(valor);
    }

    protected MockHttpServletRequestBuilder postJson(String ruta, Object cuerpo) {
        return post(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo(cuerpo));
    }

    protected JsonNode leer(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    public record Sesion(String tokenAcceso, String tokenRenovacion) {
        public String bearer() {
            return "Bearer " + tokenAcceso;
        }
    }

    protected String iniciarAlta(String dni) throws Exception {
        return leer(mvc.perform(postJson("/api/v1/auth/dni", Map.of("dni", dni, "origen", "ESCANEO", "fechaNacimiento", "1990-01-01", "sexo", "F")))
                .andExpect(status().isCreated())).get("tokenTramite").asString();
    }

    protected Sesion ingresar(String dni) throws Exception {
        admin.update("update codigos_verificacion set enviado_en = enviado_en - interval '1 hour'");
        JsonNode r = leer(mvc.perform(postJson("/api/v1/auth/ingreso", Map.of("dni", dni, "canal", "SMS")))
                .andExpect(status().isOk()));
        JsonNode s = leer(mvc.perform(postJson("/api/v1/auth/ingreso/verificar",
                Map.of("tokenIngreso", r.get("tokenIngreso").asString(), "codigo", "1234"))).andExpect(status().isOk()));
        return new Sesion(s.get("tokenAcceso").asString(), s.get("tokenRenovacion").asString());
    }

    protected Sesion administrador() throws Exception {
        return ingresar(com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_CARMEN);
    }

    protected java.util.UUID idDeUsuario(String dni) {
        return admin.queryForObject("select id from usuarios where dni = ?", java.util.UUID.class, dni);
    }

    protected java.util.UUID cuadrante(String codigo) {
        return admin.queryForObject("select id from cuadrantes where codigo = ?", java.util.UUID.class, codigo);
    }

    protected static void esperar(java.util.function.BooleanSupplier condicion) throws InterruptedException {
        long limite = System.currentTimeMillis() + 3000;
        while (!condicion.getAsBoolean()) {
            if (System.currentTimeMillis() > limite) {
                throw new AssertionError("La condición no se cumplió en 3 segundos");
            }
            Thread.sleep(25);
        }
    }

    protected Sesion registrar(String dni, String celular, double[] punto) throws Exception {
        String tramite = iniciarAlta(dni);
        mvc.perform(postJson("/api/v1/auth/codigo", Map.of("celular", celular, "canal", "SMS")).header("X-Tramite", tramite))
                .andExpect(status().isOk());
        mvc.perform(postJson("/api/v1/auth/codigo/verificar", Map.of("codigo", "1234")).header("X-Tramite", tramite))
                .andExpect(status().isOk());
        mvc.perform(postJson("/api/v1/auth/alta/domicilio", Map.of("latitud", punto[0], "longitud", punto[1]))
                .header("X-Tramite", tramite)).andExpect(status().isOk());
        JsonNode r = leer(mvc.perform(postJson("/api/v1/auth/alta", Map.of("aceptaTratamiento", true))
                .header("X-Tramite", tramite)).andExpect(status().isCreated()));
        return new Sesion(r.get("tokenAcceso").asString(), r.get("tokenRenovacion").asString());
    }
}
