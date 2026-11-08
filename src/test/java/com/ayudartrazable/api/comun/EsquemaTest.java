package com.ayudartrazable.api.comun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ayudartrazable.api.soporte.BaseDeDatosDePrueba;
import com.ayudartrazable.api.soporte.PruebaSp;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

class EsquemaTest extends PruebaSp {

    @Test
    void T010_guardiaRechazaLaBaseDeDesarrollo() {
        assertThatThrownBy(() -> BaseDeDatosDePrueba.validarDestino("jdbc:postgresql://localhost:5432/ayudar_trazable"))
                .isInstanceOf(IllegalStateException.class);
        BaseDeDatosDePrueba.validarDestino("jdbc:postgresql://localhost:5432/ayudar_trazable_pruebas?ssl=false");
    }

    @Test
    void T004_esquemaEsIdempotente() {
        BaseDeDatosDePrueba.ejecutarArchivo(Path.of("db", "db.sql"));
        BaseDeDatosDePrueba.procedimientos().forEach(BaseDeDatosDePrueba::ejecutarArchivo);
    }

    @Test
    void RNF07_rolDeAplicacionNoLeeNiBorraTablas() {
        esperarSqlState(() -> bd.queryForList("select * from parametros"), "42501");
        esperarSqlState(() -> bd.update("delete from parametros"), "42501");
        esperarSqlState(() -> bd.update("insert into parametros (clave, valor) values ('x', 'y')"), "42501");
    }

    @Test
    void RNF07_ningunRolTienePermisoDeBorradoSobreTablas() {
        Integer conDelete = admin.queryForObject("""
                select count(*) from information_schema.table_privileges
                where table_schema = 'public' and privilege_type = 'DELETE' and grantee <> 'postgres'
                """, Integer.class);
        assertThat(conDelete).isZero();
    }

    @Test
    void D04_ningunaFuncionPropiaEsEjecutablePorPublic() {
        Integer abiertas = admin.queryForObject("""
                select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace
                where n.nspname = 'public' and (p.proname like 'sp\\_%' or p.proname like 'fn\\_%')
                  and (p.proacl is null or exists (select 1 from aclexplode(p.proacl) a where a.grantee = 0))
                """, Integer.class);
        assertThat(abiertas).isZero();
    }

    @Test
    void T005_cuadrantesTienenIndiceEspacial() {
        assertThat(admin.queryForObject(
                "select indexdef from pg_indexes where indexname = 'ix_cuadrantes_poligono'", String.class))
                .contains("gist");
    }

    @Test
    void CL03_dniYCelularSonUnicos() {
        String insertar = """
                insert into usuarios (dni, nombres, apellido_paterno, apellido_materno, fecha_nacimiento, sexo, celular, tipo)
                values (?, 'A', 'B', 'C', '1990-01-01', 'F', ?, 'VECINO')
                """;
        admin.update(insertar, "45678912", "987654321");
        assertThatThrownBy(() -> admin.update(insertar, "45678912", "911111111"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> admin.update(insertar, "11111111", "987654321"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void RF11_unSoloPanicoActivoPorEmisor() {
        String usuario = admin.queryForObject("""
                insert into usuarios (dni, nombres, apellido_paterno, apellido_materno, fecha_nacimiento, sexo, celular, tipo)
                values ('45678912', 'A', 'B', 'C', '1990-01-01', 'F', '987654321', 'VECINO') returning id::text
                """, String.class);
        String insertar = """
                insert into alertas (tipo, emisor_id, punto, referencia_cuadra)
                values ('PANICO', ?::uuid, ST_SetSRID(ST_MakePoint(-76.99, -12.09), 4326), 'x')
                """;
        admin.update(insertar, usuario);
        assertThatThrownBy(() -> admin.update(insertar, usuario)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void T009_parametrosSeLeenYUsanDefectoSiFaltan() {
        assertThat(bd.queryForObject("select fn_parametro_entero('codigo_reenvio_segundos', 0)", Integer.class)).isEqualTo(30);
        assertThat(bd.queryForObject("select fn_parametro_entero('codigo_vigencia_minutos', 0)", Integer.class)).isEqualTo(5);
        assertThat(bd.queryForObject("select fn_parametro_entero('codigo_intentos_maximos', 0)", Integer.class)).isEqualTo(5);
        assertThat(bd.queryForObject("select fn_parametro_entero('edad_minima', 0)", Integer.class)).isEqualTo(18);
        assertThat(bd.queryForObject("select fn_parametro_entero('alta_vigencia_horas', 0)", Integer.class)).isEqualTo(24);
        assertThat(bd.queryForObject("select fn_parametro_entero('domicilio_cambio_dias', 0)", Integer.class)).isEqualTo(30);
        assertThat(bd.queryForObject("select fn_parametro_entero('reportes_maximos_24h', 0)", Integer.class)).isEqualTo(5);
        assertThat(bd.queryForObject("select fn_parametro_entero('falsas_alarmas_umbral', 0)", Integer.class)).isEqualTo(3);
        assertThat(bd.queryForObject("select fn_parametro_entero('falsas_alarmas_ventana_dias', 0)", Integer.class)).isEqualTo(30);
        assertThat(bd.queryForObject("select fn_parametro_entero('no_existe', 7)", Integer.class)).isEqualTo(7);
        admin.update("update parametros set valor = 'abc' where clave = 'edad_minima'");
        assertThat(bd.queryForObject("select fn_parametro_entero('edad_minima', 18)", Integer.class)).isEqualTo(18);
    }

    @Test
    void D14_fnErrorLanzaSqlstateDeNegocio() {
        esperarError(() -> bd.execute("select fn_error(409, 'PRUEBA', 'mensaje en español')"), "AT409", "PRUEBA");
    }
}
