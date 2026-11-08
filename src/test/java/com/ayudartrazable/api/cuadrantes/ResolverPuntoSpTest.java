package com.ayudartrazable.api.cuadrantes;

import static org.assertj.core.api.Assertions.assertThat;

import com.ayudartrazable.api.soporte.PruebaSp;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResolverPuntoSpTest extends PruebaSp {

    private Map<String, Object> resolver(double lat, double lng) {
        return bd.queryForMap("""
                select r->>'cuadranteId' as id, r->>'codigo' as codigo, r->>'nombre' as nombre,
                       r->>'distrito' as distrito, (r->>'dentroDeCobertura')::boolean as cubierto
                from fn_resolver_punto(?, ?) r
                """, lat, lng);
    }

    @Test
    void T020_semillaTieneTresCuadrantesYDosContiguos() {
        assertThat(admin.queryForObject("select count(*) from cuadrantes where habilitado", Integer.class))
                .isGreaterThanOrEqualTo(3);
        assertThat(admin.queryForObject("""
                select ST_Touches(a.poligono, b.poligono) from cuadrantes a, cuadrantes b
                where a.codigo = 'CUAD-SB-04' and b.codigo = 'CUAD-SB-05'
                """, Boolean.class)).isTrue();
    }

    @Test
    void RF04_puntoInterior_devuelveSuCuadrante() {
        Map<String, Object> r = resolver(-12.0905, -76.9902);
        assertThat(r).containsEntry("codigo", "CUAD-SB-04")
                .containsEntry("nombre", "San Borja · Sector 4")
                .containsEntry("distrito", "San Borja")
                .containsEntry("cubierto", true);
        assertThat(r.get("id")).isNotNull();
    }

    @Test
    void RF04_puntoFueraDeCobertura_noDevuelveCuadrante() {
        Map<String, Object> r = resolver(-12.0000, -77.1000);
        assertThat(r).containsEntry("cubierto", false);
        assertThat(r.get("id")).isNull();
    }

    @Test
    void RF04_cuadranteDeshabilitado_noCuenta() {
        admin.update("update cuadrantes set habilitado = false where codigo = 'CUAD-SB-04'");
        assertThat(resolver(-12.0905, -76.9902)).containsEntry("cubierto", false);
    }

    @Test
    void CL05_puntoEnElBorde_siempreElMismoCuadrante() {
        for (int i = 0; i < 10; i++) {
            assertThat(resolver(-12.0905, -76.9860)).containsEntry("codigo", "CUAD-SB-04");
        }
    }

    @Test
    void RF10_referenciaDeCuadra_seCalculaDelNumero() {
        assertThat(bd.queryForObject("select fn_referencia_cuadra('Jr. Jacarandá 342, San Borja 15037', 'San Borja · Sector 4')",
                String.class)).isEqualTo("Jr. Jacarandá cuadra 3");
        assertThat(bd.queryForObject("select fn_referencia_cuadra('Av. Aviación 2840, San Borja', 'x')", String.class))
                .isEqualTo("Av. Aviación cuadra 28");
        assertThat(bd.queryForObject("select fn_referencia_cuadra('Calle Los Pinos 45', 'x')", String.class))
                .isEqualTo("Calle Los Pinos cuadra 1");
        assertThat(bd.queryForObject("select fn_referencia_cuadra('Parque Central, San Borja', 'San Borja · Sector 4')",
                String.class)).isEqualTo("Parque Central");
        assertThat(bd.queryForObject("select fn_referencia_cuadra(null, 'San Borja · Sector 4')", String.class))
                .isEqualTo("San Borja · Sector 4");
    }
}
