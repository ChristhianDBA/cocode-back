package com.ayudartrazable.api.soporte;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import org.assertj.core.api.AbstractThrowableAssert;
import org.junit.jupiter.api.extension.ExtendWith;
import org.postgresql.util.PSQLException;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(ExtensionBaseDeDatos.class)
public abstract class PruebaSp {

    protected final JdbcTemplate bd = BaseDeDatosDePrueba.app();
    protected final JdbcTemplate admin = BaseDeDatosDePrueba.admin();

    protected AbstractThrowableAssert<?, ? extends Throwable> esperarError(Runnable accion, String sqlState,
            String codigo) {
        return assertThatThrownBy(accion::run)
                .satisfies(e -> {
                    PSQLException psql = buscar(e);
                    org.assertj.core.api.Assertions.assertThat(psql.getServerErrorMessage().getSQLState())
                            .isEqualTo(sqlState);
                    org.assertj.core.api.Assertions.assertThat(psql.getServerErrorMessage().getDetail())
                            .isEqualTo(codigo);
                });
    }

    protected void esperarSqlState(Runnable accion, String sqlState) {
        assertThatThrownBy(accion::run)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(buscar(e).getSQLState()).isEqualTo(sqlState));
    }

    protected static String mensajeDe(Throwable e) {
        return buscar(e).getServerErrorMessage().getMessage();
    }

    private static PSQLException buscar(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof PSQLException p) {
                return p;
            }
            if (t instanceof SQLException s && s.getNextException() instanceof PSQLException p) {
                return p;
            }
        }
        throw new AssertionError("No es un error de PostgreSQL: " + e);
    }
}
