package com.ayudartrazable.api.identidad;

import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_ANA;
import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_JUAN;
import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_MENOR;
import static org.assertj.core.api.Assertions.assertThat;

import com.ayudartrazable.api.soporte.Escenario;
import com.ayudartrazable.api.soporte.PruebaSp;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentidadSpTest extends PruebaSp {

    private final Escenario escenario = new Escenario(bd, admin);

    private String json(String sql, Object... args) {
        return bd.queryForObject(sql, String.class, args);
    }

    @Test
    void RF01_RF02_tramiteCreadoConDatosNoEditablesParaAmbosOrigenes() {
        for (String origen : new String[] {"ESCANEO", "MANUAL"}) {
            String r = json("select sp_tramite_iniciar('ALTA', ?, 'Ana María', 'Quispe', 'Ramos', '1992-03-15', 'F', ?)::text",
                    DNI_ANA, origen);
            assertThat(r).contains("\"tokenTramite\"").contains("\"editable\": false").contains("\"verificado\": true")
                    .contains("\"nombres\": \"Ana María\"").contains("\"sexo\": \"F\"");
        }
        assertThat(admin.queryForObject("select count(*) from altas_en_curso where activo", Integer.class)).isEqualTo(2);
    }

    @Test
    void RF02_dniMalFormado_rechazado() {
        esperarError(() -> json("select sp_tramite_iniciar('ALTA', '1234567', 'A', 'B', 'C', '1990-01-01', 'F', 'MANUAL')::text"),
                "AT400", "DNI_INVALIDO");
    }

    @Test
    void CL14_diecisieteRechazadoYDieciochoAceptado() {
        LocalDate diecisiete = LocalDate.now().minusYears(18).plusDays(1);
        esperarError(() -> json("select sp_tramite_iniciar('ALTA', ?, 'Diego', 'F', 'C', ?, 'M', 'MANUAL')::text", DNI_MENOR, diecisiete),
                "AT409", "MENOR_DE_EDAD")
                .satisfies(e -> assertThat(mensajeDe(e)).isEqualTo("Debes ser mayor de edad para unirte a la red vecinal"));
        LocalDate dieciocho = LocalDate.now().minusYears(18);
        assertThat(json("select sp_tramite_iniciar('ALTA', ?, 'Diego', 'F', 'C', ?, 'M', 'MANUAL')::text", DNI_MENOR, dieciocho))
                .contains("\"edad\": 18");
    }

    @Test
    void RF05_dniYaRegistrado_rechazado() {
        escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        esperarError(() -> escenario.iniciarTramite("ALTA", DNI_ANA), "AT409", "DNI_REGISTRADO")
                .satisfies(e -> assertThat(mensajeDe(e)).isEqualTo("Este DNI ya está registrado. Inicia sesión"));
    }

    @Test
    void RF03_emisionDevuelveCodigoYVigencia() {
        String token = escenario.iniciarTramite("ALTA", DNI_ANA);
        String r = json("select sp_tramite_solicitar_codigo(?, '987654321', 'WHATSAPP')::text", token);
        assertThat(r).contains("\"codigo\": \"1234\"").contains("\"canal\": \"WHATSAPP\"")
                .contains("\"venceEnSegundos\": 300").contains("\"reenvioEnSegundos\": 30");
        assertThat(admin.queryForObject("select codigo_hash <> '1234' from codigos_verificacion", Boolean.class)).isTrue();
    }

    @Test
    void RF03_CL13_reenvioAntesDe30s_rechazaConSegundosRestantes_yLuegoPermiteCambiarCanal() {
        String token = escenario.iniciarTramite("ALTA", DNI_ANA);
        json("select sp_tramite_solicitar_codigo(?, '987654321', 'SMS')::text", token);
        esperarError(() -> json("select sp_tramite_solicitar_codigo(?, '987654321', 'SMS')::text", token),
                "AT429", "REENVIO_PREMATURO")
                .satisfies(e -> assertThat(mensajeDe(e)).matches("Podrás pedir un nuevo código en (29|30) segundos"));
        escenario.envejecerCodigos(31);
        assertThat(json("select sp_tramite_solicitar_codigo(?, '987654321', 'WHATSAPP')::text", token))
                .contains("WHATSAPP");
        assertThat(admin.queryForObject("select count(*) from codigos_verificacion where activo", Integer.class)).isEqualTo(1);
    }

    @Test
    void RF03_celularMalFormadoYCanalInvalido() {
        String token = escenario.iniciarTramite("ALTA", DNI_ANA);
        esperarError(() -> json("select sp_tramite_solicitar_codigo(?, '98765432', 'SMS')::text", token), "AT400", "CELULAR_INVALIDO");
        esperarError(() -> json("select sp_tramite_solicitar_codigo(?, '987654321', 'CORREO')::text", token), "AT400", "CANAL_INVALIDO");
    }

    @Test
    void RF03_sinCodigoFijo_generaCodigosAleatoriosDeCuatroDigitosQueSeValidan() {
        admin.update("update parametros set activo = false where clave = 'codigo_fijo'");
        java.util.Set<String> codigos = new java.util.HashSet<>();
        for (int i = 0; i < 20; i++) {
            codigos.add(bd.queryForObject("select fn_generar_codigo()", String.class));
        }
        assertThat(codigos).allMatch(c -> c.matches("\\d{4}"));
        assertThat(codigos.size()).isGreaterThan(10);

        String token = escenario.iniciarTramite("ALTA", DNI_ANA);
        String codigo = bd.queryForObject("select sp_tramite_solicitar_codigo(?, '987654321', 'SMS')->>'codigo'", String.class, token);
        assertThat(codigo).matches("\\d{4}");
        assertThat(json("select sp_tramite_verificar_codigo(?, ?)::text", token, codigo)).contains("\"celularVerificado\": true");
    }

    @Test
    void RF03_codigoCorrecto_marcaCelularVerificado() {
        String token = escenario.iniciarTramite("ALTA", DNI_ANA);
        json("select sp_tramite_solicitar_codigo(?, '987654321', 'SMS')::text", token);
        assertThat(json("select sp_tramite_verificar_codigo(?, '1234')::text", token)).contains("\"celularVerificado\": true");
        assertThat(json("select fn_tramite_estado(?)::text", token)).contains("\"celularVerificado\": true");
    }

    @Test
    void RF03_codigoIncorrectoOVencido_devuelveError() {
        String token = escenario.iniciarTramite("ALTA", DNI_ANA);
        json("select sp_tramite_solicitar_codigo(?, '987654321', 'SMS')::text", token);
        assertThat(json("select sp_tramite_verificar_codigo(?, '9999')::text", token))
                .contains("CODIGO_INVALIDO").contains("El código no es válido o ya venció");
        admin.update("update codigos_verificacion set vence_en = now() - interval '1 second'");
        assertThat(json("select sp_tramite_verificar_codigo(?, '1234')::text", token)).contains("CODIGO_INVALIDO");
    }

    @Test
    void RF03_quintoIntentoFallido_invalidaElCodigo() {
        String token = escenario.iniciarTramite("ALTA", DNI_ANA);
        json("select sp_tramite_solicitar_codigo(?, '987654321', 'SMS')::text", token);
        for (int i = 1; i <= 4; i++) {
            assertThat(json("select sp_tramite_verificar_codigo(?, '0000')::text", token)).contains("CODIGO_INVALIDO");
        }
        assertThat(json("select sp_tramite_verificar_codigo(?, '0000')::text", token)).contains("CODIGO_BLOQUEADO");
        assertThat(json("select sp_tramite_verificar_codigo(?, '1234')::text", token)).contains("CODIGO_INVALIDO");
    }

    @Test
    void RF04_domicilioDentroDeCuadrante_guardaReferenciaDeCuadra() {
        String token = escenario.iniciarTramite("ALTA", DNI_ANA);
        String r = json("select sp_alta_guardar_domicilio(?, ?, ?, 'Jr. Jacarandá 342, San Borja')::text",
                token, Escenario.SB04[0], Escenario.SB04[1]);
        assertThat(r).contains("\"referenciaCuadra\": \"Jr. Jacarandá cuadra 3\"").contains("CUAD-SB-04")
                .contains("Solo vecinos verificados de tu cuadrante verán tu zona");
    }

    @Test
    void RF04_domicilioFueraDeCobertura_rechazado() {
        String token = escenario.iniciarTramite("ALTA", DNI_ANA);
        esperarError(() -> escenario.guardarDomicilio(token, Escenario.FUERA, "x"), "AT409", "SIN_COBERTURA")
                .satisfies(e -> assertThat(mensajeDe(e))
                        .isEqualTo("Tu zona aún no tiene cobertura. Te avisaremos cuando esté disponible"));
    }

    @Test
    void RF05_cadaPasoFaltanteSeIndica() {
        String token = escenario.iniciarTramite("ALTA", DNI_ANA);
        assertThat(json("select fn_tramite_estado(?)::text", token)).contains("\"faltan\": [\"CELULAR\", \"DOMICILIO\"]")
                .contains("\"puedeRegistrar\": false");
        esperarError(() -> json("select sp_alta_completar(?, true)::text", token), "AT409", "FALTA_CELULAR");
        escenario.verificarCelular(token, "987654321");
        esperarError(() -> json("select sp_alta_completar(?, true)::text", token), "AT409", "FALTA_DOMICILIO");
        escenario.guardarDomicilio(token, Escenario.SB04, "Jr. Jacarandá 342");
        esperarError(() -> json("select sp_alta_completar(?, false)::text", token), "AT409", "FALTA_ACEPTACION");
        assertThat(json("select fn_tramite_estado(?)::text", token)).contains("\"puedeRegistrar\": true");
    }

    @Test
    void CL02_tramiteVencido_rechazado() {
        String token = escenario.iniciarTramite("ALTA", DNI_ANA);
        admin.update("update altas_en_curso set vence_en = now() - interval '1 second'");
        esperarError(() -> json("select fn_tramite_estado(?)::text", token), "AT410", "TRAMITE_VENCIDO");
        esperarError(() -> json("select fn_tramite_estado(?)::text", "token-falso"), "AT401", "TRAMITE_INVALIDO");
    }

    @Test
    void RF05_celularDeOtraCuenta_rechazado() {
        escenario.vecino(DNI_JUAN, "987654321", Escenario.SB04);
        String token = escenario.iniciarTramite("ALTA", DNI_ANA);
        esperarError(() -> json("select sp_tramite_solicitar_codigo(?, '987654321', 'SMS')::text", token),
                "AT409", "CELULAR_REGISTRADO");
    }

    @Test
    void RF05_altaExitosa_creaVecinoDomicilioYSesion() {
        String token = escenario.iniciarTramite("ALTA", DNI_ANA);
        escenario.verificarCelular(token, "987654321");
        escenario.guardarDomicilio(token, Escenario.SB04, "Jr. Jacarandá 342");
        String r = json("select sp_alta_completar(?, true)::text", token);
        assertThat(r).contains("¡Bienvenida al barrio!").contains("Vecina verificada").contains("tokenRenovacion");
        assertThat(admin.queryForObject("""
                select count(*) from usuarios u join domicilios d on d.usuario_id = u.id and d.vigente
                join sesiones s on s.usuario_id = u.id
                where u.dni = ? and u.acepta_tratamiento_en is not null and d.cuadrante_id is not null
                """, Integer.class, DNI_ANA)).isEqualTo(1);
        esperarError(() -> json("select sp_alta_completar(?, true)::text", token), "AT401", "TRAMITE_INVALIDO");
    }

    @Test
    void RF05_bienvenidaMasculina() {
        String token = escenario.iniciarTramite("ALTA", DNI_JUAN);
        escenario.verificarCelular(token, "911111111");
        escenario.guardarDomicilio(token, Escenario.SB04, "x");
        assertThat(json("select sp_alta_completar(?, true)::text", token)).contains("¡Bienvenido al barrio!")
                .contains("Vecino verificado");
    }

    @Test
    void RF07_sesionGuardaSoloHashYSeValida() {
        Escenario.Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        assertThat(admin.queryForObject("select token_renovacion_hash from sesiones where id = ?", String.class, ana.sesionId()))
                .isNotEqualTo(ana.tokenRenovacion()).hasSize(64);
        assertThat(json("select fn_validar_sesion(?)::text", ana.sesionId())).contains("\"tipo\": \"VECINO\"")
                .contains("cuadranteId").contains("[\"VECINO\"]");
    }

    @Test
    void RF07_cuentaDesactivada_sesionDejaDeSerValida() {
        Escenario.Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        admin.update("update usuarios set activo = false where id = ?", ana.usuarioId());
        assertThat(json("select fn_validar_sesion(?)::text", ana.sesionId())).isNull();
    }

    @Test
    void RF07_renovacionRotaElTokenYRechazaElAnterior() {
        Escenario.Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        admin.update("update sesiones set creada_en = now() - interval '1 year'");
        String nuevo = bd.queryForObject("select sp_sesion_renovar(?)->>'tokenRenovacion'", String.class, ana.tokenRenovacion());
        assertThat(nuevo).isNotEqualTo(ana.tokenRenovacion());
        esperarError(() -> json("select sp_sesion_renovar(?)::text", ana.tokenRenovacion()), "AT401", "SESION_INVALIDA");
        assertThat(json("select sp_sesion_renovar(?)::text", nuevo)).contains(ana.sesionId().toString());
    }

    @Test
    void RF07_CL12_cerrarSesionInvalidaSoloEsaSesionYSusDispositivos() {
        Escenario.Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        UUID otra = UUID.fromString(bd.queryForObject("select sp_sesion_crear(?)->>'sesionId'", String.class, ana.usuarioId()));
        admin.update("insert into dispositivos (sesion_id, usuario_id, token_push) values (?, ?, 'tok-1'), (?, ?, 'tok-2')",
                ana.sesionId(), ana.usuarioId(), otra, ana.usuarioId());
        json("select sp_sesion_cerrar(?)::text", ana.sesionId());
        assertThat(json("select fn_validar_sesion(?)::text", ana.sesionId())).isNull();
        assertThat(json("select fn_validar_sesion(?)::text", otra)).isNotNull();
        assertThat(admin.queryForList("select token_push from dispositivos where valido", String.class)).containsExactly("tok-2");
        esperarError(() -> json("select sp_sesion_renovar(?)::text", ana.tokenRenovacion()), "AT401", "SESION_INVALIDA");
    }

    @Test
    void RF06_ingresoDniRegistrado_enviaCodigoAlCelularDeLaCuenta() {
        escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        String r = json("select sp_ingreso_solicitar(?, 'SMS')::text", DNI_ANA);
        assertThat(r).contains("\"celularEnmascarado\": \"******321\"").contains("\"celular\": \"987654321\"")
                .contains("\"codigo\": \"1234\"");
        String token = bd.queryForObject("select ?::jsonb->>'tokenIngreso'", String.class, r);
        assertThat(json("select sp_ingreso_verificar(?, '1234')::text", token)).contains("tokenRenovacion");
        assertThat(json("select sp_ingreso_verificar(?, '1234')::text", token)).contains("CODIGO_INVALIDO");
    }

    @Test
    void RF06_ingresoDniNoRegistradoOInactivo_respuestaGenericaSinEnvio() {
        String r = json("select sp_ingreso_solicitar(?, 'SMS')::text", DNI_JUAN);
        assertThat(r).contains("tokenIngreso").contains("celularEnmascarado").doesNotContain("\"envio\"");
        assertThat(json("select sp_ingreso_solicitar(?, 'SMS')::text", DNI_JUAN))
                .contains(bd.queryForObject("select ?::jsonb->>'celularEnmascarado'", String.class, r));
        String token = bd.queryForObject("select ?::jsonb->>'tokenIngreso'", String.class, r);
        assertThat(json("select sp_ingreso_verificar(?, '1234')::text", token)).contains("CODIGO_INVALIDO");
        assertThat(json("select sp_ingreso_verificar('no-es-uuid', '1234')::text")).contains("CODIGO_INVALIDO");

        Escenario.Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        admin.update("update usuarios set activo = false where id = ?", ana.usuarioId());
        assertThat(json("select sp_ingreso_solicitar(?, 'SMS')::text", DNI_ANA)).doesNotContain("\"envio\"");
    }

    @Test
    void CL13_ingresoRepetido_aplicaLimiteDeReenvio() {
        escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        escenario.envejecerCodigos(60);
        json("select sp_ingreso_solicitar(?, 'SMS')::text", DNI_ANA);
        esperarError(() -> json("select sp_ingreso_solicitar(?, 'SMS')::text", DNI_ANA), "AT429", "REENVIO_PREMATURO");
    }
}
