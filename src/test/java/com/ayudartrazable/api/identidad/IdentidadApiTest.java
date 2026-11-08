package com.ayudartrazable.api.identidad;

import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_ANA;
import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_JUAN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

import com.ayudartrazable.api.comun.errores.ExcepcionServicioNoDisponible;
import com.ayudartrazable.api.integraciones.mensajeria.Canal;
import com.ayudartrazable.api.integraciones.mensajeria.EnvioCodigoSimulado;
import com.ayudartrazable.api.integraciones.registrocivil.ConsultaRegistroCivil;
import com.ayudartrazable.api.integraciones.registrocivil.DatosCiviles;
import java.time.LocalDate;
import java.util.Optional;
import com.ayudartrazable.api.soporte.Escenario;
import com.ayudartrazable.api.soporte.PruebaApi;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;

class IdentidadApiTest extends PruebaApi {

    @MockitoSpyBean
    private ConsultaRegistroCivil registroCivil;

    @Test
    void RF02_dniDeSieteDigitos_400SinConsultarRegistroCivil() throws Exception {
        mvc.perform(postJson("/api/v1/auth/dni", Map.of("dni", "1234567", "origen", "MANUAL", "fechaNacimiento", "1990-01-01", "sexo", "F")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.campos[0].campo").value("dni"))
                .andExpect(jsonPath("$.campos[0].mensaje").value("El DNI debe tener 8 dígitos"));
        verify(registroCivil, never()).consultar(anyString());
    }

    @Test
    void T074_RF02_fechaOSexoFaltantesOFechaFutura_400SinConsultar() throws Exception {
        mvc.perform(postJson("/api/v1/auth/dni", Map.of("dni", DNI_ANA, "origen", "MANUAL", "sexo", "F")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].campo").value("fechaNacimiento"));
        mvc.perform(postJson("/api/v1/auth/dni", Map.of("dni", DNI_ANA, "origen", "MANUAL", "fechaNacimiento", "2999-01-01", "sexo", "F")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].mensaje").value("La fecha de nacimiento no puede ser futura"));
        mvc.perform(postJson("/api/v1/auth/dni", Map.of("dni", DNI_ANA, "origen", "MANUAL", "fechaNacimiento", "1990-01-01", "sexo", "X")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].campo").value("sexo"));
        verify(registroCivil, never()).consultar(anyString());
    }

    @Test
    void T074_RF01_proveedorSoloConNombres_usaFechaYSexoDelDni() throws Exception {
        doReturn(Optional.of(new DatosCiviles("41112222", "ROXANA KARINA", "DELGADO", "HUAMANI", null, null)))
                .when(registroCivil).consultar("41112222");
        mvc.perform(postJson("/api/v1/auth/dni", Map.of("dni", "41112222", "origen", "ESCANEO",
                        "fechaNacimiento", LocalDate.now().minusYears(30).toString(), "sexo", "F")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.nombres").value("ROXANA KARINA"))
                .andExpect(jsonPath("$.datos.edad").value(30))
                .andExpect(jsonPath("$.datos.sexo").value("F"));
    }

    @Test
    void T074_CL14_proveedorSoloConNombres_menorSegunElDniEsRechazado() throws Exception {
        doReturn(Optional.of(new DatosCiviles("41112222", "DIEGO", "FLORES", "CHAVEZ", null, null)))
                .when(registroCivil).consultar("41112222");
        mvc.perform(postJson("/api/v1/auth/dni", Map.of("dni", "41112222", "origen", "ESCANEO",
                        "fechaNacimiento", LocalDate.now().minusYears(18).plusDays(1).toString(), "sexo", "M")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("MENOR_DE_EDAD"));
    }

    @Test
    void T074_RF01_siElProveedorTraeFechaYSexo_prevalecen() throws Exception {
        mvc.perform(postJson("/api/v1/auth/dni", Map.of("dni", DNI_ANA, "origen", "ESCANEO",
                        "fechaNacimiento", "2000-01-01", "sexo", "M")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.sexo").value("F"))
                .andExpect(jsonPath("$.datos.edad").value(java.time.Period.between(LocalDate.of(1992, 3, 15), LocalDate.now()).getYears()));
    }

    @Test
    void RF01_dniInexistente_404() throws Exception {
        mvc.perform(postJson("/api/v1/auth/dni", Map.of("dni", "00000000", "origen", "ESCANEO", "fechaNacimiento", "1990-01-01", "sexo", "F")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No encontramos este DNI. Revisa el número e inténtalo de nuevo"));
    }

    @Test
    void CL01_registroCivilCaido_503() throws Exception {
        doThrow(new ExcepcionServicioNoDisponible("No pudimos validar tu DNI en este momento. Intenta más tarde"))
                .when(registroCivil).consultar(DNI_ANA);
        mvc.perform(postJson("/api/v1/auth/dni", Map.of("dni", DNI_ANA, "origen", "ESCANEO", "fechaNacimiento", "1990-01-01", "sexo", "F")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value("No pudimos validar tu DNI en este momento. Intenta más tarde"));
    }

    @Test
    void RF01_dniValido_autollenaDatosNoEditables() throws Exception {
        mvc.perform(postJson("/api/v1/auth/dni", Map.of("dni", DNI_ANA, "origen", "ESCANEO", "fechaNacimiento", "1990-01-01", "sexo", "F")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenTramite").isNotEmpty())
                .andExpect(jsonPath("$.datos.dni").value("******12"))
                .andExpect(jsonPath("$.datos.nombres").value("Ana María"))
                .andExpect(jsonPath("$.datos.apellidoPaterno").value("Quispe"))
                .andExpect(jsonPath("$.datos.edad").isNumber())
                .andExpect(jsonPath("$.datos.sexo").value("F"))
                .andExpect(jsonPath("$.datos.editable").value(false));
    }

    @Test
    void RF03_envioPorSmsYWhatsappYValidacionDeCelular() throws Exception {
        String tramite = iniciarAlta(DNI_ANA);
        mvc.perform(postJson("/api/v1/auth/codigo", Map.of("celular", "98765432", "canal", "SMS")).header("X-Tramite", tramite))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].campo").value("celular"));
        mvc.perform(postJson("/api/v1/auth/codigo", Map.of("celular", "987654321", "canal", "SMS")).header("X-Tramite", tramite))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venceEnSegundos").value(300))
                .andExpect(jsonPath("$.reenvioEnSegundos").value(30))
                .andExpect(content().string(not(containsString("1234"))));
        assertThat(mensajes.ultimo("987654321")).contains(new EnvioCodigoSimulado.Envio("1234", Canal.SMS));

        mvc.perform(postJson("/api/v1/auth/codigo", Map.of("celular", "987654321", "canal", "WHATSAPP")).header("X-Tramite", tramite))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("REENVIO_PREMATURO"));
        admin.update("update codigos_verificacion set enviado_en = enviado_en - interval '31 seconds'");
        mvc.perform(postJson("/api/v1/auth/codigo", Map.of("celular", "987654321", "canal", "WHATSAPP")).header("X-Tramite", tramite))
                .andExpect(status().isOk());
        assertThat(mensajes.ultimo("987654321")).contains(new EnvioCodigoSimulado.Envio("1234", Canal.WHATSAPP));

        mvc.perform(postJson("/api/v1/auth/codigo/verificar", Map.of("codigo", "9999")).header("X-Tramite", tramite))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El código no es válido o ya venció"));
        mvc.perform(postJson("/api/v1/auth/codigo/verificar", Map.of("codigo", "1234")).header("X-Tramite", tramite))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.celularVerificado").value(true));
    }

    @Test
    void RF04_resolverPinYGuardarDomicilio() throws Exception {
        String tramite = iniciarAlta(DNI_ANA);
        mvc.perform(get("/api/v1/geo/resolver-pin").param("lat", "-12.0905").param("lng", "-76.9902"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/geo/resolver-pin").param("lat", "-12.0905").param("lng", "-76.9902").header("X-Tramite", tramite))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dentroDeCobertura").value(true))
                .andExpect(jsonPath("$.nombre").value("San Borja · Sector 4"))
                .andExpect(jsonPath("$.distrito").value("San Borja"))
                .andExpect(jsonPath("$.direccionEstimada").value("Jr. Jacarandá 342, San Borja"));
        mvc.perform(get("/api/v1/geo/resolver-pin").param("lat", "-12.0").param("lng", "-77.1").header("X-Tramite", tramite))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dentroDeCobertura").value(false));
        mvc.perform(postJson("/api/v1/auth/alta/domicilio", Map.of("latitud", -12.0, "longitud", -77.1)).header("X-Tramite", tramite))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Tu zona aún no tiene cobertura. Te avisaremos cuando esté disponible"));
        mvc.perform(postJson("/api/v1/auth/alta/domicilio", Map.of("latitud", -12.0905, "longitud", -76.9902)).header("X-Tramite", tramite))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenciaCuadra").value("Jr. Jacarandá cuadra 3"));
    }

    @Test
    void RF05_estadoIndicaLoQueFaltaYBienvenidaSegunSexo() throws Exception {
        String tramite = iniciarAlta(DNI_ANA);
        mvc.perform(get("/api/v1/auth/tramite").header("X-Tramite", tramite))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faltan[0]").value("CELULAR"))
                .andExpect(jsonPath("$.faltan[1]").value("DOMICILIO"))
                .andExpect(jsonPath("$.puedeRegistrar").value(false));
        mvc.perform(postJson("/api/v1/auth/alta", Map.of("aceptaTratamiento", true)).header("X-Tramite", tramite))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("FALTA_CELULAR"));

        JsonNode ana = leer(mvc.perform(postJson("/api/v1/auth/alta", Map.of("aceptaTratamiento", true))
                .header("X-Tramite", tramiteCompleto(DNI_JUAN, "911111111"))).andExpect(status().isCreated()));
        assertThat(ana.get("mensajeBienvenida").asString()).isEqualTo("¡Bienvenido al barrio!");
        assertThat(ana.get("insignia").asString()).isEqualTo("Vecino verificado");
        assertThat(ana.get("tokenAcceso").asString()).isNotBlank();
        assertThat(ana.get("expiraEnSegundos").asInt()).isEqualTo(900);
    }

    private String tramiteCompleto(String dni, String celular) throws Exception {
        String tramite = iniciarAlta(dni);
        mvc.perform(postJson("/api/v1/auth/codigo", Map.of("celular", celular, "canal", "SMS")).header("X-Tramite", tramite));
        mvc.perform(postJson("/api/v1/auth/codigo/verificar", Map.of("codigo", "1234")).header("X-Tramite", tramite));
        mvc.perform(postJson("/api/v1/auth/alta/domicilio", Map.of("latitud", -12.0905, "longitud", -76.9902)).header("X-Tramite", tramite));
        return tramite;
    }

    @Test
    void RF05_bienvenidaFemenina() throws Exception {
        mvc.perform(postJson("/api/v1/auth/alta", Map.of("aceptaTratamiento", true))
                        .header("X-Tramite", tramiteCompleto(DNI_ANA, "987654321")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensajeBienvenida").value("¡Bienvenida al barrio!"))
                .andExpect(jsonPath("$.insignia").value("Vecina verificada"));
    }

    @Test
    void CL03_dosAltasSimultaneasDelMismoDni_soloUnaCuenta() throws Exception {
        String t1 = tramiteCompleto(DNI_ANA, "987654321");
        admin.update("update altas_en_curso set activo = true");
        String t2 = tramiteCompleto(DNI_ANA, "911111111");
        admin.update("update altas_en_curso set activo = true");
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        List<Callable<Integer>> tareas = List.of(t1, t2).stream().<Callable<Integer>>map(t -> () ->
                mvc.perform(postJson("/api/v1/auth/alta", Map.of("aceptaTratamiento", true)).header("X-Tramite", t))
                        .andReturn().getResponse().getStatus()).toList();
        List<Integer> estados = hilos.invokeAll(tareas).stream().map(f -> {
            try {
                return f.get();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).toList();
        hilos.shutdown();
        assertThat(estados).containsExactlyInAnyOrder(201, 409);
        assertThat(admin.queryForObject("select count(*) from usuarios where dni = ?", Integer.class, DNI_ANA)).isEqualTo(1);
    }

    @Test
    void RF07_sesionSeUsaSeRenuevaYSeCierra() throws Exception {
        Sesion s = registrar(DNI_ANA, "987654321", Escenario.SB04);
        JsonNode renovada = leer(mvc.perform(postJson("/api/v1/auth/renovar", Map.of("tokenRenovacion", s.tokenRenovacion())))
                .andExpect(status().isOk()));
        mvc.perform(postJson("/api/v1/auth/renovar", Map.of("tokenRenovacion", s.tokenRenovacion())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESION_INVALIDA"));
        String acceso = "Bearer " + renovada.get("tokenAcceso").asString();
        mvc.perform(post("/api/v1/auth/salir").header("Authorization", acceso)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/salir").header("Authorization", acceso)).andExpect(status().isUnauthorized());
    }

    @Test
    void RF07_cuentaDesactivada_siguienteSolicitudRechazada() throws Exception {
        Sesion s = registrar(DNI_ANA, "987654321", Escenario.SB04);
        admin.update("update usuarios set activo = false where dni = ?", DNI_ANA);
        mvc.perform(post("/api/v1/auth/salir").header("Authorization", s.bearer()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void RF06_ingresoConDniRegistradoYNoRegistradoMismaForma() throws Exception {
        registrar(DNI_ANA, "987654321", Escenario.SB04);
        admin.update("update codigos_verificacion set enviado_en = enviado_en - interval '1 hour'");
        mensajes.limpiar();
        JsonNode real = leer(mvc.perform(postJson("/api/v1/auth/ingreso", Map.of("dni", DNI_ANA, "canal", "SMS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.celularEnmascarado").value("******321"))
                .andExpect(content().string(not(containsString("987654321")))));
        assertThat(mensajes.ultimo("987654321")).isPresent();

        JsonNode falso = leer(mvc.perform(postJson("/api/v1/auth/ingreso", Map.of("dni", DNI_JUAN, "canal", "SMS")))
                .andExpect(status().isOk()));
        assertThat(falso.propertyNames()).containsExactlyInAnyOrderElementsOf(real.propertyNames());

        mvc.perform(postJson("/api/v1/auth/ingreso/verificar", Map.of("tokenIngreso", falso.get("tokenIngreso").asString(), "codigo", "1234")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El código no es válido o ya venció"));
        mvc.perform(postJson("/api/v1/auth/ingreso/verificar", Map.of("tokenIngreso", real.get("tokenIngreso").asString(), "codigo", "1234")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenAcceso").isNotEmpty())
                .andExpect(jsonPath("$.tokenRenovacion").isNotEmpty());
    }

    @Test
    void RNF10_fallaDelProveedorDeSms_anulaElCodigoYPermiteReintentar() throws Exception {
        String tramite = iniciarAlta(DNI_ANA);
        mensajes.fallarProximo();
        mvc.perform(postJson("/api/v1/auth/codigo", Map.of("celular", "987654321", "canal", "SMS")).header("X-Tramite", tramite))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(postJson("/api/v1/auth/codigo", Map.of("celular", "987654321", "canal", "SMS")).header("X-Tramite", tramite))
                .andExpect(status().isOk());
    }
}
