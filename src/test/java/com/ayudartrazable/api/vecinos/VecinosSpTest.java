package com.ayudartrazable.api.vecinos;

import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_ANA;
import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_JUAN;
import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_LUIS;
import static com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado.DNI_ROSA;
import static org.assertj.core.api.Assertions.assertThat;

import com.ayudartrazable.api.soporte.Escenario;
import com.ayudartrazable.api.soporte.Escenario.Vecino;
import com.ayudartrazable.api.soporte.PruebaSp;
import org.junit.jupiter.api.Test;

class VecinosSpTest extends PruebaSp {

    private final Escenario escenario = new Escenario(bd, admin);

    private String json(String sql, Object... args) {
        return bd.queryForObject(sql, String.class, args);
    }

    @Test
    void RF08_cambioExitoso_reasignaCuadrante() {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        String r = json("select sp_domicilio_cambiar(?, ?, ?, 'Av. Aviación 2840')::text", ana.usuarioId(),
                Escenario.SB05[0], Escenario.SB05[1]);
        assertThat(r).contains("CUAD-SB-05").contains("Av. Aviación cuadra 28");
        assertThat(json("select fn_validar_sesion(?)->>'cuadranteId'", ana.sesionId()))
                .isEqualTo(admin.queryForObject("select id::text from cuadrantes where codigo = 'CUAD-SB-05'", String.class));
        assertThat(admin.queryForObject("select count(*) from domicilios where usuario_id = ?", Integer.class, ana.usuarioId()))
                .isEqualTo(2);
    }

    @Test
    void RF08_antesDe30Dias_rechazaConFecha() {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        json("select sp_domicilio_cambiar(?, ?, ?, 'x')::text", ana.usuarioId(), Escenario.SB05[0], Escenario.SB05[1]);
        esperarError(() -> json("select sp_domicilio_cambiar(?, ?, ?, 'x')::text", ana.usuarioId(),
                Escenario.SB04[0], Escenario.SB04[1]), "AT409", "CAMBIO_PREMATURO")
                .satisfies(e -> assertThat(mensajeDe(e)).matches("Podrás cambiar tu domicilio a partir del \\d{2}/\\d{2}/\\d{4}"));
        admin.update("update usuarios set ultimo_cambio_domicilio_en = now() - interval '31 days'");
        assertThat(json("select sp_domicilio_cambiar(?, ?, ?, 'x')::text", ana.usuarioId(), Escenario.SB04[0], Escenario.SB04[1]))
                .contains("CUAD-SB-04");
    }

    @Test
    void RF08_fueraDeCobertura_conservaElActual() {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        esperarError(() -> json("select sp_domicilio_cambiar(?, ?, ?, 'x')::text", ana.usuarioId(),
                Escenario.FUERA[0], Escenario.FUERA[1]), "AT409", "SIN_COBERTURA");
        assertThat(admin.queryForObject("select c.codigo from domicilios d join cuadrantes c on c.id = d.cuadrante_id where d.vigente",
                String.class)).isEqualTo("CUAD-SB-04");
    }

    @Test
    void RF08_conPanicoActivo_rechazado() {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        admin.update("insert into alertas (tipo, emisor_id, punto, referencia_cuadra) values ('PANICO', ?, fn_punto(-12.09, -76.99), 'x')",
                ana.usuarioId());
        esperarError(() -> json("select sp_domicilio_cambiar(?, ?, ?, 'x')::text", ana.usuarioId(),
                Escenario.SB05[0], Escenario.SB05[1]), "AT409", "PANICO_ACTIVO");
    }

    @Test
    void RF09_cuadrantePropioYCercanos() {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        assertThat(json("select fn_cuadrante_propio(?)::text", ana.usuarioId())).contains("San Borja · Sector 4")
                .contains("\"distrito\": \"San Borja\"").contains("\"type\": \"Polygon\"");
        String cercanos = json("select fn_cuadrantes_cercanos(?)::text", ana.usuarioId());
        assertThat(cercanos).contains("San Borja · Sector 5").doesNotContain("Miraflores").doesNotContain("cuadranteId")
                .doesNotContain("Sector 4");
    }

    @Test
    void RF10_RNF02_redDelCuadranteConPrivacidad() {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        escenario.vecino(DNI_JUAN, "911111111", Escenario.SB04);
        escenario.vecino(DNI_ROSA, "922222222", Escenario.SB05);
        Vecino luis = escenario.vecino(DNI_LUIS, "933333333", Escenario.SB04);
        admin.update("update usuarios set activo = false where id = ?", luis.usuarioId());

        String red = json("select fn_vecinos_cuadrante(?)::text", ana.usuarioId());
        assertThat(red).contains("\"nombre\": \"Juan Carlos P.\"").contains("\"referenciaCuadra\": \"Jr. Jacarandá cuadra 3\"")
                .contains("Vecino verificado")
                .doesNotContain("Ana María").doesNotContain("Rosa").doesNotContain("Luis")
                .doesNotContain(DNI_JUAN).doesNotContain("911111111").doesNotContain("-12.09").doesNotContain("edad");
    }

    @Test
    void RF10_RF21_sinCuadranteNoVeNada() {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        admin.update("update domicilios set cuadrante_id = null");
        esperarError(() -> json("select fn_vecinos_cuadrante(?)::text", ana.usuarioId()), "AT409", "SIN_COBERTURA")
                .satisfies(e -> assertThat(mensajeDe(e)).isEqualTo("Tu zona aún no tiene cobertura"));
        esperarError(() -> json("select fn_cuadrante_propio(?)::text", ana.usuarioId()), "AT409", "SIN_COBERTURA");
        assertThat(json("select fn_perfil(?)::text", ana.usuarioId())).contains("\"sinCobertura\": true");
    }

    @Test
    void RF14_registroActualizacionEInvalidacionDeDispositivo() {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        json("select sp_dispositivo_registrar(?, ?, 'tok-1')::text", ana.usuarioId(), ana.sesionId());
        json("select sp_dispositivo_registrar(?, ?, 'tok-2')::text", ana.usuarioId(), ana.sesionId());
        assertThat(admin.queryForList("select token_push from dispositivos where valido", String.class)).containsExactly("tok-2");
        json("select sp_dispositivo_invalidar('tok-2')::text");
        assertThat(admin.queryForObject("select count(*) from dispositivos where valido", Integer.class)).isZero();
        esperarError(() -> json("select sp_dispositivo_registrar(?, ?, '')::text", ana.usuarioId(), ana.sesionId()),
                "AT400", "TOKEN_PUSH_INVALIDO");
    }

    @Test
    void RF18_perfilConInsigniaYDniEnmascarado() {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        Vecino juan = escenario.vecino(DNI_JUAN, "911111111", Escenario.SB04);
        assertThat(json("select fn_perfil(?)::text", ana.usuarioId())).contains("Vecina verificada")
                .contains("\"dni\": \"******12\"").contains("\"celular\": \"987654321\"").contains("CUAD-SB-04")
                .contains("\"domicilio\": true").doesNotContain(DNI_ANA);
        assertThat(json("select fn_perfil(?)::text", juan.usuarioId())).contains("Vecino verificado");
    }

    @Test
    void RF18_telefonosDelDistritoONacionales() {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        Vecino juan = escenario.vecino(DNI_JUAN, "911111111", Escenario.MF01);
        assertThat(json("select fn_telefonos_emergencia(?)::text", ana.usuarioId())).contains("\"fuente\": \"DISTRITO\"")
                .contains("Serenazgo").contains("Comisaría").contains("Bomberos");
        assertThat(json("select fn_telefonos_emergencia(?)::text", juan.usuarioId())).contains("\"fuente\": \"NACIONAL\"")
                .contains("\"numero\": \"105\"").contains("\"numero\": \"116\"");
        admin.update("update domicilios set cuadrante_id = null where usuario_id = ?", ana.usuarioId());
        assertThat(json("select fn_telefonos_emergencia(?)::text", ana.usuarioId())).contains("\"fuente\": \"NACIONAL\"");
    }

    @Test
    void RF20_reporteDeDatosCivilesNoModificaDatos() {
        Vecino ana = escenario.vecino(DNI_ANA, "987654321", Escenario.SB04);
        assertThat(json("select sp_reporte_datos_civiles(?, 'Mi segundo apellido es Ramírez')::text", ana.usuarioId()))
                .contains("RENIEC").contains("\"recibido\": true");
        assertThat(admin.queryForObject("select apellido_materno from usuarios where id = ?", String.class, ana.usuarioId()))
                .isEqualTo("Ramos");
        assertThat(admin.queryForObject("select count(*) from avisos_administrador where tipo = 'DATOS_CIVILES'", Integer.class))
                .isEqualTo(1);
        esperarError(() -> json("select sp_reporte_datos_civiles(?, '  ')::text", ana.usuarioId()), "AT400", "DESCRIPCION_REQUERIDA");
    }
}
