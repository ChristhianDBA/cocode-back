package com.ayudartrazable.api.comun;

import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_ANA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.ayudartrazable.api.soporte.Escenario;
import com.ayudartrazable.api.soporte.PruebaApi;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

@ExtendWith(OutputCaptureExtension.class)
class CierreTest extends PruebaApi {

    private static final Set<String> PUBLICAS = Set.of(
            "POST /api/v1/auth/dni", "GET /api/v1/auth/tramite", "POST /api/v1/auth/codigo",
            "POST /api/v1/auth/codigo/verificar", "POST /api/v1/auth/alta", "POST /api/v1/auth/alta/domicilio",
            "POST /api/v1/auth/ingreso", "POST /api/v1/auth/ingreso/verificar", "POST /api/v1/auth/renovar",
            "POST /api/v1/auth/recuperacion/dni", "POST /api/v1/auth/recuperacion/completar",
            "GET /api/v1/geo/resolver-pin");

    private static final List<String> RUTAS_DEL_PLAN = List.of(
            "POST /api/v1/auth/dni", "GET /api/v1/auth/tramite", "POST /api/v1/auth/codigo",
            "POST /api/v1/auth/codigo/verificar", "GET /api/v1/geo/resolver-pin", "POST /api/v1/auth/alta/domicilio",
            "POST /api/v1/auth/alta", "POST /api/v1/auth/ingreso", "POST /api/v1/auth/ingreso/verificar",
            "POST /api/v1/auth/renovar", "POST /api/v1/auth/salir", "POST /api/v1/auth/recuperacion/dni",
            "POST /api/v1/auth/recuperacion/completar", "PUT /api/v1/vecinos/me/domicilio", "GET /api/v1/geo/cuadrantes/mio",
            "GET /api/v1/geo/cuadrantes/cercanos", "GET /api/v1/geo/cuadrantes/mio/vecinos", "POST /api/v1/emergencias/panico",
            "POST /api/v1/emergencias/reportes", "GET /api/v1/emergencias/activas", "PATCH /api/v1/emergencias/{alertaId}/estado",
            "PUT /api/v1/vecinos/me/dispositivo", "GET /api/v1/vecinos/me", "GET /api/v1/vecinos/telefonos-emergencia",
            "POST /api/v1/vecinos/me/reporte-datos", "POST /api/v1/vecinos/me/recuperacion/cancelar", "GET /api/v1/admin/usuarios", "POST /api/v1/admin/coordinadores",
            "POST /api/v1/admin/coordinadores/{usuarioId}/retirar", "POST /api/v1/admin/serenazgo",
            "POST /api/v1/admin/serenazgo/{usuarioId}/desactivar", "GET /api/v1/admin/avisos",
            "POST /api/v1/admin/suspensiones/{suspensionId}/levantar", "POST /api/v1/admin/cuadrantes/{cuadranteId}/deshabilitar",
            "POST /api/v1/admin/cuadrantes/{cuadranteId}/habilitar");

    private List<String> rutasDocumentadas() throws Exception {
        JsonNode docs = leer(mvc.perform(get("/v3/api-docs")));
        List<String> rutas = new ArrayList<>();
        docs.get("paths").properties().forEach(p -> p.getValue().propertyNames()
                .forEach(m -> rutas.add(m.toUpperCase() + " " + p.getKey())));
        return rutas;
    }

    private static String concreta(String plantilla) {
        return plantilla.replaceAll("\\{[^}]+}", "00000000-0000-0000-0000-000000000000");
    }

    private int estado(String ruta, String bearer) throws Exception {
        String[] partes = ruta.split(" ");
        var solicitud = request(HttpMethod.valueOf(partes[0]), concreta(partes[1]))
                .contentType(MediaType.APPLICATION_JSON).content("{}");
        if (bearer != null) {
            solicitud.header("Authorization", bearer);
        }
        return mvc.perform(solicitud).andReturn().getResponse().getStatus();
    }

    @Test
    void T069_catalogoOpenApiTieneTodasLasRutasDelPlan() throws Exception {
        assertThat(rutasDocumentadas()).containsAll(RUTAS_DEL_PLAN);
    }

    @Test
    void T066_RNF04_matrizDeAutorizacionSobreTodasLasRutas() throws Exception {
        Sesion vecino = registrar(DNI_ANA, "987654321", Escenario.SB04);
        List<String> fallas = new ArrayList<>();
        for (String ruta : rutasDocumentadas()) {
            if (!ruta.contains("/api/v1/")) {
                continue;
            }
            int sinToken = estado(ruta, null);
            if (PUBLICAS.contains(ruta)) {
                if (sinToken == 401 && !ruta.contains("resolver-pin")) {
                    fallas.add("Ruta pública exige token: " + ruta);
                }
            } else if (sinToken != 401) {
                fallas.add("Sin token debería ser 401: " + ruta + " -> " + sinToken);
            }
            if (ruta.contains("/api/v1/admin/") && estado(ruta, vecino.bearer()) != 403) {
                fallas.add("Vecino no debería acceder: " + ruta);
            }
            if (ruta.startsWith("PATCH /api/v1/emergencias/") && estado(ruta, vecino.bearer()) != 403) {
                fallas.add("Vecino sin rol no debería cambiar estados: " + ruta);
            }
        }
        assertThat(fallas).isEmpty();
    }

    @Test
    void T067_RNF05_flujosCompletosNoDejanDatosSensiblesEnLogs(CapturedOutput salida) throws Exception {
        Sesion ana = registrar(DNI_ANA, "987654321", Escenario.SB04);
        Sesion juan = ingresar(DNI_ANA);
        mvc.perform(postJson("/api/v1/emergencias/panico", Map.of("confirmado", true, "latitud", -12.090512, "longitud", -76.990234))
                .header("Authorization", ana.bearer()));
        String tramite = leer(mvc.perform(postJson("/api/v1/auth/recuperacion/dni", Map.of("dni", DNI_ANA, "origen", "MANUAL", "fechaNacimiento", "1990-01-01", "sexo", "F"))))
                .get("tokenTramite").asString();
        mvc.perform(postJson("/api/v1/auth/codigo", Map.of("celular", "944444444", "canal", "SMS")).header("X-Tramite", tramite));
        mvc.perform(postJson("/api/v1/auth/codigo/verificar", Map.of("codigo", "1234")).header("X-Tramite", tramite));
        mvc.perform(post("/api/v1/auth/recuperacion/completar").header("X-Tramite", tramite));
        admin.update("update altas_en_curso set aplica_en = now() - interval '1 second' where proposito = 'RECUPERACION'");
        mvc.perform(post("/api/v1/auth/recuperacion/completar").header("X-Tramite", tramite));
        mvc.perform(postJson("/api/v1/auth/dni", Map.of("dni", "00000000", "origen", "MANUAL", "fechaNacimiento", "1990-01-01", "sexo", "F")));

        assertThat(salida.getAll())
                .doesNotContain(DNI_ANA)
                .doesNotContain("987654321")
                .doesNotContain("944444444")
                .doesNotContain(ana.tokenAcceso())
                .doesNotContain(ana.tokenRenovacion())
                .doesNotContain(juan.tokenRenovacion())
                .doesNotContain(tramite)
                .doesNotContain("-12.090512");
    }
}
