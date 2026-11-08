package com.ayudartrazable.api.administracion;

import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_ANA;
import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_JUAN;
import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_ROSA;
import static org.assertj.core.api.Assertions.assertThat;

import com.ayudartrazable.api.soporte.Escenario;
import com.ayudartrazable.api.soporte.Escenario.Vecino;
import com.ayudartrazable.api.soporte.PruebaSp;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdministracionSpTest extends PruebaSp {

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

    @Test
    void RF16_adminInicialExisteYNoSePuedeCrearOtroDesdeLaApp() {
        assertThat(admin.queryForObject("select count(*) from usuarios where tipo = 'ADMIN'", Integer.class)).isEqualTo(1);
        esperarSqlState(() -> json("select sp_admin_inicial('1', 'a', 'b', 'c', '1990-01-01', 'F', '900000002')::text"), "42501");
    }

    @Test
    void RF16_soloElAdminGestionaRoles() {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        Vecino juan = escenario.vecino(DNI_JUAN, "911111111", Escenario.SB04);
        esperarError(() -> json("select sp_coordinador_asignar(?, ?, ?)::text", ana.usuarioId(), juan.usuarioId(), new UUID[] {sb04}),
                "AT403", "SIN_PERMISO");
    }

    @Test
    void RF16_coordinadorEnDosCuadrantesYRetiroInmediato() {
        Vecino juan = escenario.vecino(DNI_JUAN, "911111111", Escenario.SB04);
        assertThat(json("select sp_coordinador_asignar(?, ?, ?)::text", adminId, juan.usuarioId(), new UUID[] {sb04, sb05}))
                .contains("\"asignados\": 2");
        assertThat(json("select fn_validar_sesion(?)::text", juan.sesionId())).contains("COORDINADOR");
        json("select sp_coordinador_retirar(?, ?, null)::text", adminId, juan.usuarioId());
        assertThat(json("select fn_validar_sesion(?)::text", juan.sesionId())).doesNotContain("COORDINADOR");
    }

    @Test
    void RF16_coordinadorDebeSerVecinoVerificado() {
        UUID sereno = UUID.fromString(json("select sp_serenazgo_alta(?, '50000001', 'P', 'R', 'L', '1980-01-01', 'M', '955555555', ?)->>'usuarioId'",
                adminId, sanBorja));
        esperarError(() -> json("select sp_coordinador_asignar(?, ?, ?)::text", adminId, sereno, new UUID[] {sb04}),
                "AT409", "NO_VERIFICADO");
    }

    @Test
    void RF16_CL16_serenazgoAltaIngresoDesactivacionYReemplazo() {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        UUID sereno = UUID.fromString(json("select sp_serenazgo_alta(?, '50000001', 'Pedro', 'Ríos', 'Luna', '1980-01-01', 'M', '955555555', ?)->>'usuarioId'",
                adminId, sanBorja));
        String ingreso = json("select sp_ingreso_solicitar('50000001', 'SMS')::text");
        assertThat(ingreso).contains("\"envio\"");
        String token = bd.queryForObject("select ?::jsonb->>'tokenIngreso'", String.class, ingreso);
        UUID sesion = UUID.fromString(json("select sp_ingreso_verificar(?, '1234')->>'sesionId'", token));
        assertThat(json("select fn_validar_sesion(?)::text", sesion)).contains("SERENAZGO").contains("\"distritoId\": " + sanBorja);
        assertThat(json("select fn_vecinos_cuadrante(?)::text", ana.usuarioId())).doesNotContain("Pedro");

        esperarError(() -> json("select sp_serenazgo_alta(?, '50000002', 'X', 'Y', 'Z', '1980-01-01', 'M', '955555555', ?)::text",
                adminId, sanBorja), "AT409", "CELULAR_REGISTRADO");
        json("select sp_serenazgo_desactivar(?, ?)::text", adminId, sereno);
        assertThat(json("select fn_validar_sesion(?)::text", sesion)).isNull();
        assertThat(json("select sp_serenazgo_alta(?, '50000002', 'Lucía', 'Paz', 'Soto', '1985-01-01', 'F', '966666666', ?)::text",
                adminId, sanBorja)).contains("usuarioId");
    }

    @Test
    void RF17_RF19_RF20_avisosDelAdministrador() {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        json("select sp_reporte_datos_civiles(?, 'apellido mal escrito')::text", ana.usuarioId());
        assertThat(json("select fn_avisos_administrador(?)::text", adminId)).contains("DATOS_CIVILES").contains("Ana María Q.");
        esperarError(() -> json("select fn_avisos_administrador(?)::text", ana.usuarioId()), "AT403", "SIN_PERMISO");
    }

    @Test
    void RF21_CL06_deshabilitarCierraAlertasYReasigna() {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        Vecino juan = escenario.vecino(DNI_JUAN, "911111111", new double[] {-12.0905, -76.9860});
        json("select sp_dispositivo_registrar(?, ?, 'tok-ana')::text", ana.usuarioId(), ana.sesionId());
        UUID alerta = UUID.fromString(json("select sp_panico_emitir(?, true, -12.0905, -76.9902)->'alerta'->>'alertaId'", ana.usuarioId()));

        String r = json("select sp_cuadrante_deshabilitar(?, ?)::text", adminId, sb04);
        assertThat(r).contains(alerta.toString()).contains(juan.usuarioId().toString()).contains(sb05.toString())
                .contains("\"sinCuadrante\": [\"" + ana.usuarioId() + "\"]").contains("tok-ana");
        assertThat(admin.queryForObject("select estado from alertas where id = ?", String.class, alerta)).isEqualTo("RESUELTA");
        assertThat(admin.queryForObject("select autor_sistema from alertas_historial where alerta_id = ? and estado_nuevo = 'RESUELTA'",
                Boolean.class, alerta)).isTrue();
        assertThat(admin.queryForObject("select count(*) from suspensiones_panico", Integer.class)).isZero();
        esperarError(() -> json("select fn_vecinos_cuadrante(?)::text", ana.usuarioId()), "AT409", "SIN_COBERTURA");
        assertThat(json("select fn_telefonos_emergencia(?)::text", ana.usuarioId())).contains("NACIONAL");

        String h = json("select sp_cuadrante_habilitar(?, ?)::text", adminId, sb04);
        assertThat(h).contains(ana.usuarioId().toString()).contains("tok-ana");
        assertThat(json("select fn_cuadrante_propio(?)::text", ana.usuarioId())).contains("CUAD-SB-04");
    }

    @Test
    void RF19_CL04_CL15_recuperacionReemplazaCelularYRevocaSesiones() {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        escenario.vecino(DNI_ROSA, "922222222", Escenario.SB04);
        json("select sp_dispositivo_registrar(?, ?, 'tok-viejo')::text", ana.usuarioId(), ana.sesionId());
        UUID alerta = UUID.fromString(json("select sp_panico_emitir(?, true, -12.0905, -76.9902)->'alerta'->>'alertaId'", ana.usuarioId()));

        String token = escenario.iniciarTramite("RECUPERACION", DNI_ANA);
        esperarError(() -> json("select sp_tramite_solicitar_codigo(?, '922222222', 'SMS')::text", token), "AT409", "CELULAR_REGISTRADO");
        escenario.verificarCelular(token, "944444444");
        String espera = json("select sp_recuperacion_completar(?)::text", token);
        assertThat(espera).contains("\"pendiente\": true").contains("aplicaEn").contains("\"celularAnterior\": \"987654321\"")
                .contains("tok-viejo").doesNotContain("tokenRenovacion");
        assertThat(json("select fn_validar_sesion(?)::text", ana.sesionId())).isNotNull();
        esperarError(() -> json("select sp_recuperacion_completar(?)::text", token), "AT409", "RECUPERACION_EN_ESPERA")
                .satisfies(e -> assertThat(mensajeDe(e)).matches("Podrás completar la recuperación a partir del \\d{2}/\\d{2}/\\d{4} a las \\d{2}:\\d{2}"));
        assertThat(admin.queryForObject("select celular from usuarios where id = ?", String.class, ana.usuarioId())).isEqualTo("987654321");

        admin.update("update altas_en_curso set aplica_en = now() - interval '1 second' where proposito = 'RECUPERACION'");
        String r = json("select sp_recuperacion_completar(?)::text", token);
        assertThat(r).contains("tokenRenovacion");

        assertThat(json("select fn_validar_sesion(?)::text", ana.sesionId())).isNull();
        assertThat(admin.queryForObject("select celular from usuarios where id = ?", String.class, ana.usuarioId())).isEqualTo("944444444");
        assertThat(admin.queryForObject("select count(*) from dispositivos where valido", Integer.class)).isZero();
        assertThat(admin.queryForObject("select count(*) from avisos_administrador where tipo = 'RECUPERACION'", Integer.class)).isEqualTo(1);
        assertThat(admin.queryForObject("select emisor_id from alertas where id = ? and estado = 'ACTIVA'", UUID.class, alerta))
                .isEqualTo(ana.usuarioId());
        escenario.envejecerCodigos(60);
        assertThat(json("select sp_ingreso_solicitar(?, 'SMS')::text", DNI_ANA)).contains("\"celular\": \"944444444\"");
    }

    @Test
    void RF19_titularCancelaDuranteLaEspera() {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        String token = escenario.iniciarTramite("RECUPERACION", DNI_ANA);
        escenario.verificarCelular(token, "944444444");
        json("select sp_recuperacion_completar(?)::text", token);
        assertThat(json("select sp_recuperacion_cancelar(?)::text", ana.usuarioId())).contains("\"canceladas\": 1");
        admin.update("update altas_en_curso set aplica_en = now() - interval '1 second' where proposito = 'RECUPERACION'");
        esperarError(() -> json("select sp_recuperacion_completar(?)::text", token), "AT409", "RECUPERACION_CANCELADA")
                .satisfies(e -> assertThat(mensajeDe(e)).isEqualTo("El titular de la cuenta canceló la recuperación"));
        assertThat(admin.queryForObject("select celular from usuarios where id = ?", String.class, ana.usuarioId())).isEqualTo("987654321");
        assertThat(json("select fn_validar_sesion(?)::text", ana.sesionId())).isNotNull();
    }

    @Test
    void RF19_esperaSeConfiguraPorParametro() {
        escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        admin.update("update parametros set valor = '48' where clave = 'recuperacion_espera_horas'");
        String token = escenario.iniciarTramite("RECUPERACION", DNI_ANA);
        escenario.verificarCelular(token, "944444444");
        json("select sp_recuperacion_completar(?)::text", token);
        assertThat(admin.queryForObject("""
                select aplica_en between now() + interval '47 hours' and now() + interval '49 hours'
                from altas_en_curso where proposito = 'RECUPERACION'""", Boolean.class)).isTrue();
        assertThat(admin.queryForObject("select vence_en > aplica_en from altas_en_curso where proposito = 'RECUPERACION'",
                Boolean.class)).isTrue();
    }

    @Test
    void RF19_dniSinCuenta_noRecupera() {
        String token = escenario.iniciarTramite("RECUPERACION", DNI_ANA);
        escenario.verificarCelular(token, "944444444");
        esperarError(() -> json("select sp_recuperacion_completar(?)::text", token), "AT409", "RECUPERACION_INVALIDA");
    }
}
