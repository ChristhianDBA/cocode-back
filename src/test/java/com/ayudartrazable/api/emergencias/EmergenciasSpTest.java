package com.ayudartrazable.api.emergencias;

import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_ANA;
import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_JUAN;
import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_LUIS;
import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_ROSA;
import static org.assertj.core.api.Assertions.assertThat;

import com.ayudartrazable.api.soporte.Escenario;
import com.ayudartrazable.api.soporte.Escenario.Vecino;
import com.ayudartrazable.api.soporte.PruebaSp;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class EmergenciasSpTest extends PruebaSp {

    private final Escenario escenario = new Escenario(bd, admin);
    private UUID adminId;
    private UUID sb04;
    private UUID sb05;
    private int sanBorja;

    @BeforeEach
    void preparar() {
        adminId = admin.queryForObject("select id from usuarios where tipo = 'ADMIN'", UUID.class);
        sb04 = admin.queryForObject("select id from cuadrantes where codigo = 'CUAD-SB-04'", UUID.class);
        sb05 = admin.queryForObject("select id from cuadrantes where codigo = 'CUAD-SB-05'", UUID.class);
        sanBorja = admin.queryForObject("select id from distritos where nombre = 'San Borja'", Integer.class);
    }

    private String json(String sql, Object... args) {
        return bd.queryForObject(sql, String.class, args);
    }

    private String panico(Vecino v, double[] punto) {
        return json("select sp_panico_emitir(?, true, ?, ?)::text", v.usuarioId(), punto == null ? null : punto[0],
                punto == null ? null : punto[1]);
    }

    private UUID alertaDe(String resultado) {
        return UUID.fromString(bd.queryForObject("select ?::jsonb->'alerta'->>'alertaId'", String.class, resultado));
    }

    private void coordinador(Vecino v, UUID... cuadrantes) {
        json("select sp_coordinador_asignar(?, ?, ?)::text", adminId, v.usuarioId(), cuadrantes);
    }

    private UUID serenazgo(String dni, String celular) {
        return UUID.fromString(json("select sp_serenazgo_alta(?, ?, 'Pedro', 'Ríos', 'Luna', '1980-01-01', 'M', ?, ?)->>'usuarioId'",
                adminId, dni, celular, sanBorja));
    }

    private void dispositivo(Vecino v, String token) {
        json("select sp_dispositivo_registrar(?, ?, ?)::text", v.usuarioId(), v.sesionId(), token);
    }

    @Test
    void RF11_sinConfirmacion_rechazado() {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        esperarError(() -> json("select sp_panico_emitir(?, false, -12.09, -76.99)::text", ana.usuarioId()),
                "AT400", "CONFIRMACION_REQUERIDA");
    }

    @Test
    void RF11_panicoEnSuCuadrante_destinatariosYPrivacidad() {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        Vecino juan = escenario.vecino(DNI_JUAN, "911111111", Escenario.SB04);
        Vecino rosa = escenario.vecino(DNI_ROSA, "922222222", Escenario.SB05);
        dispositivo(ana, "tok-ana");
        dispositivo(juan, "tok-juan");
        dispositivo(rosa, "tok-rosa");
        UUID sereno = serenazgo("50000001", "955555555");

        String r = panico(ana, new double[] {-12.0905, -76.9902});
        assertThat(r).contains("\"creada\": true").contains("\"emisor\": \"Ana María Q.\"")
                .contains("\"referenciaCuadra\": \"Jr. Jacarandá cuadra 3\"").contains(sb04.toString())
                .contains(sereno.toString()).contains("tok-juan").doesNotContain("tok-rosa").doesNotContain("tok-ana");
        assertThat(bd.queryForObject("select (?::jsonb->'alerta'->'latitud') is not null", Boolean.class, r)).isFalse();
        assertThat(bd.queryForObject("select (?::jsonb->'alertaConUbicacion'->'latitud') is not null", Boolean.class, r)).isTrue();
    }

    @Test
    void RF11_segundoPanico_devuelveElVigente() {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        UUID primera = alertaDe(panico(ana, Escenario.SB04));
        String segunda = panico(ana, Escenario.SB04);
        assertThat(segunda).contains("\"creada\": false");
        assertThat(alertaDe(segunda)).isEqualTo(primera);
        assertThat(admin.queryForObject("select count(*) from alertas", Integer.class)).isEqualTo(1);
    }

    @Test
    void CL08_panicoEnOtroCuadrante_llegaAlActualYCoordinadoresDelDomicilio() {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        Vecino coordSb04 = escenario.vecino(DNI_JUAN, "911111111", Escenario.SB04);
        Vecino vecinaSb05 = escenario.vecino(DNI_ROSA, "922222222", Escenario.SB05);
        coordinador(coordSb04, sb04);
        dispositivo(vecinaSb05, "tok-rosa");
        String r = panico(ana, Escenario.SB05);
        assertThat(r).contains("\"cuadrantes\": [\"" + sb05 + "\"]").contains(coordSb04.usuarioId().toString())
                .contains("tok-rosa").contains("\"referenciaCuadra\": \"San Borja · Sector 5\"")
                .contains("\"fueraDeCobertura\": false");
    }

    @Test
    void RF11_fueraDeCobertura_avisaCoordinadoresYSerenazgoDelDomicilio() {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        Vecino coord = escenario.vecino(DNI_JUAN, "911111111", Escenario.SB04);
        coordinador(coord, sb04);
        UUID sereno = serenazgo("50000001", "955555555");
        String r = panico(ana, Escenario.FUERA);
        assertThat(r).contains("\"fueraDeCobertura\": true").contains("\"cuadrantes\": []")
                .contains(coord.usuarioId().toString()).contains(sereno.toString());
    }

    @Test
    void CL07_sinGps_usaDomicilioYMarcaAproximada() {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        String r = panico(ana, null);
        assertThat(r).contains("\"ubicacionAproximada\": true").contains("Jr. Jacarandá cuadra 3").contains(sb04.toString());
    }

    @Test
    void RF11_dosPanicosSimultaneos_unaSolaAlerta() throws Exception {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        List<Callable<String>> tareas = List.of(1, 2).stream().<Callable<String>>map(i -> () -> {
            JdbcTemplate propio = new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(
                    com.ayudartrazable.api.soporte.BaseDeDatosDePrueba.url(), "postgres",
                    System.getenv().getOrDefault("PGPASSWORD", ""), true));
            propio.execute("set role app_ayudar");
            return propio.queryForObject("select sp_panico_emitir(?, true, -12.0905, -76.9902)::text", String.class,
                    ana.usuarioId());
        }).toList();
        hilos.invokeAll(tareas).forEach(f -> {
            try {
                f.get();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        hilos.shutdown();
        assertThat(admin.queryForObject("select count(*) from alertas", Integer.class)).isEqualTo(1);
    }

    @Test
    void RF12_reporteValidoLimiteYTipo() {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        UUID sereno = serenazgo("50000001", "955555555");
        String r = json("select sp_reporte_emitir(?, 'SOSPECHA', 'Persona merodeando')::text", ana.usuarioId());
        assertThat(r).contains("\"tipo\": \"SOSPECHA\"").contains(sereno.toString());
        esperarError(() -> json("select sp_reporte_emitir(?, null, 'x')::text", ana.usuarioId()), "AT400", "TIPO_INVALIDO");
        esperarError(() -> json("select sp_reporte_emitir(?, 'ROBO', ' ')::text", ana.usuarioId()), "AT400", "DESCRIPCION_REQUERIDA");
        for (int i = 0; i < 4; i++) {
            json("select sp_reporte_emitir(?, 'ALUMBRADO', 'Poste apagado')::text", ana.usuarioId());
        }
        esperarError(() -> json("select sp_reporte_emitir(?, 'ROBO', 'x')::text", ana.usuarioId()), "AT429", "LIMITE_REPORTES")
                .satisfies(e -> assertThat(mensajeDe(e)).matches("Alcanzaste el límite de 5 reportes en 24 horas\\. Podrás enviar otro a partir de las \\d{2}:\\d{2}"));
    }

    @Test
    void RF13_alertasActivasOrdenadasYAisladasPorCuadrante() {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        Vecino juan = escenario.vecino(DNI_JUAN, "911111111", Escenario.SB04);
        Vecino rosa = escenario.vecino(DNI_ROSA, "922222222", Escenario.SB05);
        json("select sp_reporte_emitir(?, 'SOSPECHA', 'primero')::text", ana.usuarioId());
        json("select sp_reporte_emitir(?, 'ROBO', 'segundo')::text", juan.usuarioId());
        String activas = json("select fn_alertas_activas(?)::text", juan.usuarioId());
        assertThat(activas.indexOf("segundo")).isLessThan(activas.indexOf("primero"));
        assertThat(activas).doesNotContain("latitud");
        assertThat(json("select fn_alertas_activas(?)::text", rosa.usuarioId())).isEqualTo("[]");
        assertThat(bd.queryForObject("select fn_puede_suscribir(?, ?)", Boolean.class, ana.usuarioId(), sb04)).isTrue();
        assertThat(bd.queryForObject("select fn_puede_suscribir(?, ?)", Boolean.class, ana.usuarioId(), sb05)).isFalse();
    }

    @Test
    void RF13_serenazgoVeAlertasDeSuDistritoConUbicacion() {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        UUID sereno = serenazgo("50000001", "955555555");
        panico(ana, Escenario.SB04);
        assertThat(json("select fn_alertas_activas(?)::text", sereno)).contains("latitud").contains("PANICO");
        assertThat(bd.queryForObject("select fn_puede_suscribir(?, ?)", Boolean.class, sereno, sb05)).isTrue();
    }

    @Test
    void RF15_CL10_permisosDeCambioDeEstadoEHistorial() {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        Vecino coord = escenario.vecino(DNI_JUAN, "911111111", Escenario.SB04);
        Vecino coordOtro = escenario.vecino(DNI_ROSA, "922222222", Escenario.SB05);
        Vecino vecino = escenario.vecino(DNI_LUIS, "933333333", Escenario.SB04);
        coordinador(coord, sb04);
        coordinador(coordOtro, sb05);
        UUID alerta = alertaDe(panico(ana, Escenario.SB04));

        esperarError(() -> json("select sp_alerta_cambiar_estado(?, ?, 'ATENDIDA')::text", coordOtro.usuarioId(), alerta), "AT403", "SIN_PERMISO");
        esperarError(() -> json("select sp_alerta_cambiar_estado(?, ?, 'ATENDIDA')::text", vecino.usuarioId(), alerta), "AT403", "SIN_PERMISO");
        coordinador(ana, sb04);
        esperarError(() -> json("select sp_alerta_cambiar_estado(?, ?, 'RESUELTA')::text", ana.usuarioId(), alerta), "AT403", "PROPIA_ALERTA");

        assertThat(json("select sp_alerta_cambiar_estado(?, ?, 'ATENDIDA')::text", coord.usuarioId(), alerta)).contains("ATENDIDA");
        assertThat(json("select fn_alertas_activas(?)::text", vecino.usuarioId())).contains(alerta.toString());
        UUID sereno = serenazgo("50000001", "955555555");
        json("select sp_alerta_cambiar_estado(?, ?, 'RESUELTA')::text", sereno, alerta);
        assertThat(json("select fn_alertas_activas(?)::text", vecino.usuarioId())).isEqualTo("[]");
        assertThat(admin.queryForList("select estado_nuevo from alertas_historial where alerta_id = ? order by fecha", String.class, alerta))
                .containsExactly("ACTIVA", "ATENDIDA", "RESUELTA");
        assertThat(admin.queryForObject("select count(*) from alertas_historial where autor_id is null and not autor_sistema", Integer.class)).isZero();
        esperarError(() -> json("select sp_alerta_cambiar_estado(?, ?, 'ATENDIDA')::text", coord.usuarioId(), alerta), "AT409", "ALERTA_CERRADA");
        esperarError(() -> json("select sp_alerta_cambiar_estado(?, ?, 'BORRADA')::text", coord.usuarioId(), alerta), "AT400", "ESTADO_INVALIDO");
    }

    @Test
    void RF17_tresFalsasAlarmasEn30Dias_suspendenYAvisan() {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        Vecino coord = escenario.vecino(DNI_JUAN, "911111111", Escenario.SB04);
        coordinador(coord, sb04);
        for (int i = 1; i <= 2; i++) {
            UUID a = alertaDe(panico(ana, Escenario.SB04));
            assertThat(json("select sp_alerta_cambiar_estado(?, ?, 'FALSA_ALARMA')::text", coord.usuarioId(), a))
                    .contains("\"emisorSuspendido\": false");
        }
        UUID tercera = alertaDe(panico(ana, Escenario.SB04));
        assertThat(json("select sp_alerta_cambiar_estado(?, ?, 'FALSA_ALARMA')::text", coord.usuarioId(), tercera))
                .contains("\"emisorSuspendido\": true");
        assertThat(admin.queryForObject("select count(*) from avisos_administrador where tipo = 'SUSPENSION'", Integer.class)).isEqualTo(1);
        esperarError(() -> panico(ana, Escenario.SB04), "AT403", "PANICO_SUSPENDIDO")
                .satisfies(e -> assertThat(mensajeDe(e)).contains("3 falsas alarmas en 30 días"));
        assertThat(json("select sp_reporte_emitir(?, 'ROBO', 'sigo pudiendo reportar')::text", ana.usuarioId())).contains("ROBO");

        UUID suspension = admin.queryForObject("select id from suspensiones_panico", UUID.class);
        json("select sp_suspension_levantar(?, ?)::text", adminId, suspension);
        assertThat(panico(ana, Escenario.SB04)).contains("\"creada\": true");
    }

    @Test
    void RF17_falsasAlarmasFueraDeLaVentana_noSuspenden() {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        Vecino coord = escenario.vecino(DNI_JUAN, "911111111", Escenario.SB04);
        coordinador(coord, sb04);
        for (int i = 1; i <= 2; i++) {
            json("select sp_alerta_cambiar_estado(?, ?, 'FALSA_ALARMA')::text", coord.usuarioId(), alertaDe(panico(ana, Escenario.SB04)));
        }
        admin.update("update alertas_historial set fecha = fecha - interval '31 days' where estado_nuevo = 'FALSA_ALARMA'");
        UUID tercera = alertaDe(panico(ana, Escenario.SB04));
        assertThat(json("select sp_alerta_cambiar_estado(?, ?, 'FALSA_ALARMA')::text", coord.usuarioId(), tercera))
                .contains("\"emisorSuspendido\": false");
    }
}
