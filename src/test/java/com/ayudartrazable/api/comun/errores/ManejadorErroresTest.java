package com.ayudartrazable.api.comun.errores;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.http.MediaType;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class ManejadorErroresTest {

    record SolicitudPrueba(@Pattern(regexp = "\\d{8}", message = "El DNI debe tener 8 dígitos") String dni) {}

    @RestController
    static class ControladorPrueba {
        @PostMapping("/validar")
        String validar(@Valid @RequestBody SolicitudPrueba solicitud) {
            return "ok";
        }

        @GetMapping("/negocio")
        String negocio() {
            throw new ExcepcionNegocio(409, "DNI_REGISTRADO", "Este DNI ya está registrado. Inicia sesión");
        }

        @GetMapping("/bd")
        String bd() {
            throw errorBd("SERROR\0CAT429\0Mdebes esperar 12 segundos\0DREENVIO_PREMATURO\0\0");
        }

        @GetMapping("/bd-desconocido")
        String bdDesconocido() {
            throw errorBd("SERROR\0C23505\0Mllave duplicada (dni)=(12345678)\0\0");
        }

        @GetMapping("/inesperado")
        String inesperado() {
            throw new IllegalStateException("detalle interno 12345678");
        }

        @GetMapping("/no-disponible")
        String noDisponible() {
            throw new ExcepcionServicioNoDisponible("No pudimos validar tu DNI en este momento. Intenta más tarde");
        }

        private static UncategorizedSQLException errorBd(String mensajeServidor) {
            SQLException causa = new PSQLException(new ServerErrorMessage(mensajeServidor));
            return new UncategorizedSQLException("llamada", "select 1", causa);
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(new ControladorPrueba())
                .setControllerAdvice(new ManejadorErrores())
                .build();
    }

    @Test
    void errorDeValidacion_devuelve400ConCampos() throws Exception {
        mvc.perform(post("/validar").contentType(MediaType.APPLICATION_JSON).content("{\"dni\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.mensaje").value("Revisa los datos ingresados"))
                .andExpect(jsonPath("$.campos[0].campo").value("dni"))
                .andExpect(jsonPath("$.campos[0].mensaje").value("El DNI debe tener 8 dígitos"));
    }

    @Test
    void cuerpoMalFormado_devuelve400SinDetalleTecnico() throws Exception {
        mvc.perform(post("/validar").contentType(MediaType.APPLICATION_JSON).content("{dni"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"))
                .andExpect(jsonPath("$.mensaje").value("El formato de la solicitud no es válido"))
                .andExpect(jsonPath("$.campos").isEmpty());
    }

    @Test
    void excepcionDeNegocio_usaSuEstadoCodigoYMensaje() throws Exception {
        mvc.perform(get("/negocio"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DNI_REGISTRADO"))
                .andExpect(jsonPath("$.mensaje").value("Este DNI ya está registrado. Inicia sesión"));
    }

    @Test
    void errorDeNegocioDesdeSp_traduceSqlstateADetalleYEstado() throws Exception {
        mvc.perform(get("/bd"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("REENVIO_PREMATURO"))
                .andExpect(jsonPath("$.mensaje").value("debes esperar 12 segundos"));
    }

    @Test
    void errorDeBdNoControlado_devuelve500GenericoSinFiltrarDatos() throws Exception {
        mvc.perform(get("/bd-desconocido"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(content().string(not(containsString("12345678"))));
    }

    @Test
    void errorInesperado_devuelve500Generico() throws Exception {
        mvc.perform(get("/inesperado"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.mensaje").value("Ocurrió un error inesperado. Intenta más tarde"))
                .andExpect(content().string(not(containsString("detalle interno"))));
    }

    @Test
    void servicioExternoCaido_devuelve503() throws Exception {
        mvc.perform(get("/no-disponible"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("SERVICIO_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.mensaje").value("No pudimos validar tu DNI en este momento. Intenta más tarde"));
    }
}
