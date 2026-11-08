package com.ayudartrazable.api.vecinos;

import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_ANA;
import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_JUAN;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ayudartrazable.api.soporte.Escenario;
import com.ayudartrazable.api.soporte.PruebaApi;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class VecinosApiTest extends PruebaApi {

    @Test
    void RF08_cambioDeDomicilio_dejaDeVerAlertasDelCuadranteAnterior() throws Exception {
        Sesion ana = registrar(DNI_ANA, "987654321", Escenario.SB04);
        Sesion juan = registrar(DNI_JUAN, "911111111", Escenario.SB04);
        mvc.perform(postJson("/api/v1/emergencias/reportes", Map.of("tipo", "ROBO", "descripcion", "Robo de celular"))
                .header("Authorization", juan.bearer())).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/emergencias/activas").header("Authorization", ana.bearer()))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(put("/api/v1/vecinos/me/domicilio").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("latitud", Escenario.SB05[0], "longitud", Escenario.SB05[1])))
                        .header("Authorization", ana.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("CUAD-SB-05"));
        mvc.perform(get("/api/v1/emergencias/activas").header("Authorization", ana.bearer()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void RF09_RF10_cuadranteCercanosYRedSinDatosSensibles() throws Exception {
        Sesion ana = registrar(DNI_ANA, "987654321", Escenario.SB04);
        registrar(DNI_JUAN, "911111111", Escenario.SB04);
        mvc.perform(get("/api/v1/geo/cuadrantes/mio").header("Authorization", ana.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("San Borja · Sector 4"))
                .andExpect(jsonPath("$.poligono.type").value("Polygon"));
        mvc.perform(get("/api/v1/geo/cuadrantes/cercanos").header("Authorization", ana.bearer()))
                .andExpect(jsonPath("$[0].nombre").value("San Borja · Sector 5"))
                .andExpect(jsonPath("$[0].cuadranteId").doesNotExist());
        mvc.perform(get("/api/v1/geo/cuadrantes/mio/vecinos").header("Authorization", ana.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Juan Carlos P."))
                .andExpect(content().string(not(containsString(DNI_JUAN))))
                .andExpect(content().string(not(containsString("911111111"))))
                .andExpect(content().string(not(containsString("-12.09"))));
    }

    @Test
    void RF14_RF18_RF20_perfilDispositivoTelefonosYReporte() throws Exception {
        Sesion ana = registrar(DNI_ANA, "987654321", Escenario.SB04);
        mvc.perform(get("/api/v1/vecinos/me").header("Authorization", ana.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insignia").value("Vecina verificada"))
                .andExpect(jsonPath("$.dni").value("******12"))
                .andExpect(jsonPath("$.verificaciones.domicilio").value(true))
                .andExpect(jsonPath("$.cuadrante.codigo").value("CUAD-SB-04"));
        mvc.perform(put("/api/v1/vecinos/me/dispositivo").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("tokenPush", "tok-ana"))).header("Authorization", ana.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrado").value(true));
        mvc.perform(get("/api/v1/vecinos/telefonos-emergencia").header("Authorization", ana.bearer()))
                .andExpect(jsonPath("$.fuente").value("DISTRITO"))
                .andExpect(jsonPath("$.telefonos[0].servicio").value("Serenazgo"));
        mvc.perform(postJson("/api/v1/vecinos/me/reporte-datos", Map.of("descripcion", "Mi apellido está mal"))
                        .header("Authorization", ana.bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje").value(containsString("RENIEC")));
    }

    @Test
    void RNF04_sinTokenNoHayAccesoAVecinos() throws Exception {
        mvc.perform(get("/api/v1/vecinos/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/geo/cuadrantes/mio/vecinos")).andExpect(status().isUnauthorized());
    }

    @Test
    void RF21_vecinoSinCuadrante_recibeMensajeDeCoberturaPeroVePerfilYTelefonos() throws Exception {
        Sesion ana = registrar(DNI_ANA, "987654321", Escenario.SB04);
        Sesion admin = administrador();
        mvc.perform(postJson("/api/v1/admin/cuadrantes/" + cuadrante("CUAD-SB-04") + "/deshabilitar", Map.of())
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/geo/cuadrantes/mio/vecinos").header("Authorization", ana.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Tu zona aún no tiene cobertura"));
        mvc.perform(get("/api/v1/emergencias/activas").header("Authorization", ana.bearer()))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/vecinos/me").header("Authorization", ana.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinCobertura").value(true));
        mvc.perform(get("/api/v1/vecinos/telefonos-emergencia").header("Authorization", ana.bearer()))
                .andExpect(status().isOk());
    }
}
