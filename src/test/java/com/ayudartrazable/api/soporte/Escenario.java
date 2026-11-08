package com.ayudartrazable.api.soporte;

import com.ayudartrazable.api.integraciones.registrocivil.DatosCiviles;
import com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class Escenario {

    public static final double[] SB04 = {-12.0905, -76.9902};
    public static final double[] SB05 = {-12.0905, -76.9800};
    public static final double[] MF01 = {-12.1200, -77.0300};
    public static final double[] FUERA = {-12.0000, -77.1000};

    public record Vecino(UUID usuarioId, UUID sesionId, String tokenRenovacion, String dni, String celular) {
    }

    private final JdbcTemplate bd;
    private final JdbcTemplate admin;

    public Escenario(JdbcTemplate bd, JdbcTemplate admin) {
        this.bd = bd;
        this.admin = admin;
    }

    public String iniciarTramite(String proposito, String dni) {
        DatosCiviles d = new RegistroCivilSimulado().consultar(dni).orElseThrow();
        return bd.queryForObject("select sp_tramite_iniciar(?, ?, ?, ?, ?, ?, ?, 'MANUAL')->>'tokenTramite'",
                String.class, proposito, d.dni(), d.nombres(), d.apellidoPaterno(), d.apellidoMaterno(),
                d.fechaNacimiento(), d.sexo());
    }

    public void verificarCelular(String token, String celular) {
        bd.queryForObject("select sp_tramite_solicitar_codigo(?, ?, 'SMS')::text", String.class, token, celular);
        String r = bd.queryForObject("select sp_tramite_verificar_codigo(?, '1234')::text", String.class, token);
        if (r.contains("error")) {
            throw new IllegalStateException(r);
        }
    }

    public void guardarDomicilio(String token, double[] punto, String direccion) {
        bd.queryForObject("select sp_alta_guardar_domicilio(?, ?, ?, ?)::text", String.class, token, punto[0], punto[1],
                direccion);
    }

    public Vecino vecino(String dni, String celular, double[] punto) {
        String token = iniciarTramite("ALTA", dni);
        verificarCelular(token, celular);
        guardarDomicilio(token, punto, "Jr. Jacarandá 342, San Borja");
        Map<String, Object> r = bd.queryForMap("""
                select (x->>'usuarioId')::uuid as usuario, (x->>'sesionId')::uuid as sesion, x->>'tokenRenovacion' as token
                from sp_alta_completar(?, true) x
                """, token);
        return new Vecino((UUID) r.get("usuario"), (UUID) r.get("sesion"), (String) r.get("token"), dni, celular);
    }

    public void envejecerCodigos(int segundos) {
        admin.update("update codigos_verificacion set enviado_en = enviado_en - make_interval(secs => ?)", segundos);
    }
}
