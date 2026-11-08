package com.ayudartrazable.api.comun;

import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_ANA;
import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_CARMEN;
import static org.assertj.core.api.Assertions.assertThat;

import com.ayudartrazable.api.soporte.BaseDeDatosDePrueba;
import com.ayudartrazable.api.soporte.Escenario;
import com.ayudartrazable.api.soporte.Escenario.Vecino;
import com.ayudartrazable.api.soporte.PruebaSp;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class AuditoriaSpTest extends PruebaSp {

    private final Escenario escenario = new Escenario(bd, admin);

    private String json(String sql, Object... args) {
        return bd.queryForObject(sql, String.class, args);
    }

    private static JdbcTemplate conexionPropia() {
        JdbcTemplate t = new JdbcTemplate(new SingleConnectionDataSource(BaseDeDatosDePrueba.url(), "postgres",
                System.getenv().getOrDefault("PGPASSWORD", ""), true));
        t.execute("set role app_ayudar");
        return t;
    }

    private static List<String> enParalelo(int n, Function<JdbcTemplate, String> accion) throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(n);
        List<Callable<String>> tareas = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            tareas.add(() -> {
                try {
                    return accion.apply(conexionPropia());
                } catch (RuntimeException e) {
                    return "ERROR";
                }
            });
        }
        List<String> r = hilos.invokeAll(tareas).stream().map(f -> {
            try {
                return f.get();
            } catch (Exception e) {
                return "ERROR";
            }
        }).toList();
        hilos.shutdown();
        return r;
    }

    @Test
    void A1_recuperacionNoPermiteTomarCuentaDeAdministradorNiSerenazgo() {
        String token = escenario.iniciarTramite("RECUPERACION", DNI_CARMEN);
        escenario.verificarCelular(token, "944444444");
        esperarError(() -> json("select sp_recuperacion_completar(?)::text", token), "AT409", "RECUPERACION_INVALIDA");
        assertThat(admin.queryForObject("select celular from usuarios where dni = ?", String.class, DNI_CARMEN))
                .isEqualTo("900000001");
    }

    @Test
    void A4_referenciaDeCuadraNuncaMuestraElNumeroExacto() {
        assertThat(bd.queryForObject("select fn_referencia_cuadra('Av. Aviación 2450-2460, San Borja', 'Sector 4')", String.class))
                .doesNotContain("2450").doesNotContain("2460");
        assertThat(bd.queryForObject("select fn_referencia_cuadra('Calle Las Begonias 475 Piso 3', 'Sector 4')", String.class))
                .doesNotContain("475");
        assertThat(bd.queryForObject("select fn_referencia_cuadra('Mz. B Lt. 12, Urb. Los Pinos', 'Sector 4')", String.class))
                .isEqualTo("Sector 4");
        assertThat(bd.queryForObject("select fn_referencia_cuadra('Jr. Jacarandá 342, San Borja', 'Sector 4')", String.class))
                .isEqualTo("Jr. Jacarandá cuadra 3");
    }

    @Test
    void A5_solicitudesSimultaneasNoDejanVariosCodigosActivos() throws Exception {
        escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        escenario.envejecerCodigos(3600);
        enParalelo(6, t -> t.queryForObject("select sp_ingreso_solicitar(?, 'SMS')::text", String.class, DNI_ANA));
        assertThat(admin.queryForObject("""
                select count(*) from codigos_verificacion c join usuarios u on u.id = c.referencia
                where u.dni = ? and c.activo and c.proposito = 'INGRESO'""", Integer.class, DNI_ANA)).isEqualTo(1);
    }

    @Test
    void A5_demasiadosCodigosEnUnaHora_bloqueanTemporalmente() {
        escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        for (int i = 0; i < 5; i++) {
            escenario.envejecerCodigos(31);
            json("select sp_ingreso_solicitar(?, 'SMS')::text", DNI_ANA);
        }
        escenario.envejecerCodigos(31);
        esperarError(() -> json("select sp_ingreso_solicitar(?, 'SMS')::text", DNI_ANA), "AT429", "DEMASIADOS_CODIGOS");

    }

    @Test
    void A6_codigoFijoSeInformaParaQueLaAppLoBloqueeFueraDeDev() {
        assertThat(bd.queryForObject("select fn_codigo_fijo_activo()", Boolean.class)).isTrue();
        admin.update("update parametros set activo = false where clave = 'codigo_fijo'");
        assertThat(bd.queryForObject("select fn_codigo_fijo_activo()", Boolean.class)).isFalse();
    }

    @Test
    void A7_reportesSimultaneosRespetanElLimite() throws Exception {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        enParalelo(12, t -> t.queryForObject("select sp_reporte_emitir(?, 'ROBO', 'x')::text", String.class, ana.usuarioId()));
        assertThat(admin.queryForObject("select count(*) from alertas where tipo <> 'PANICO'", Integer.class)).isEqualTo(5);
    }

    @Test
    void A8_ubicacionAproximadaNoRevelaCoordenadasDelDomicilio() {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        String reporte = json("select sp_reporte_emitir(?, 'ROBO', 'x')::text", ana.usuarioId());
        assertThat(bd.queryForObject("select (?::jsonb->'alertaConUbicacion'->'latitud') is not null", Boolean.class, reporte)).isFalse();
        String panico = json("select sp_panico_emitir(?, true, null, null)::text", ana.usuarioId());
        assertThat(bd.queryForObject("select (?::jsonb->'alertaConUbicacion'->'latitud') is not null", Boolean.class, panico)).isFalse();
    }

    @Test
    void A10_iniciarOtroTramiteNoAnulaElDeLaVictima() {
        String victima = escenario.iniciarTramite("ALTA", DNI_ANA);
        escenario.iniciarTramite("ALTA", DNI_ANA);
        assertThat(json("select fn_tramite_estado(?)::text", victima)).contains("\"proposito\": \"ALTA\"");
    }

    @Test
    void A11_cambiosDeDomicilioSimultaneos_noTerminanEnErrorInterno() throws Exception {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        List<String> r = enParalelo(4, t -> {
            try {
                return t.queryForObject("select sp_domicilio_cambiar(?, ?, ?, 'x')::text", String.class,
                        ana.usuarioId(), Escenario.SB05[0], Escenario.SB05[1]);
            } catch (org.springframework.dao.DataAccessException e) {
                return e.getMessage().contains("CAMBIO_PREMATURO") || e.getMessage().contains("Podrás cambiar") ? "PREMATURO" : "OTRO";
            }
        });
        assertThat(r).doesNotContain("OTRO");
        assertThat(admin.queryForObject("select count(*) from domicilios where usuario_id = ?", Integer.class, ana.usuarioId()))
                .isEqualTo(2);

    }
}
