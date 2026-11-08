package com.ayudartrazable.api.soporte;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

public final class BaseDeDatosDePrueba {

    public static final String BASE_PERMITIDA = "ayudar_trazable_pruebas";
    private static final Path DB = Path.of("db");
    private static volatile boolean recreada;
    private static JdbcTemplate admin;
    private static JdbcTemplate app;

    private BaseDeDatosDePrueba() {
    }

    public static String url() {
        String url = System.getenv().getOrDefault("BD_PRUEBAS_URL",
                "jdbc:postgresql://localhost:5432/" + BASE_PERMITIDA);
        validarDestino(url);
        return url;
    }

    public static void validarDestino(String url) {
        String sinParametros = url.split("\\?")[0];
        String base = sinParametros.substring(sinParametros.lastIndexOf('/') + 1);
        if (!BASE_PERMITIDA.equals(base)) {
            throw new IllegalStateException("Las pruebas solo pueden usar la base " + BASE_PERMITIDA + " y se configuró " + base);
        }
    }

    public static synchronized JdbcTemplate admin() {
        if (admin == null) {
            admin = new JdbcTemplate(fuente(false));
            String actual = admin.queryForObject("select current_database()", String.class);
            if (!BASE_PERMITIDA.equals(actual)) {
                throw new IllegalStateException("Conectado a una base distinta de " + BASE_PERMITIDA);
            }
        }
        return admin;
    }

    public static synchronized JdbcTemplate app() {
        if (app == null) {
            app = new JdbcTemplate(fuente(true));
        }
        return app;
    }

    private static DataSource fuente(boolean comoApp) {
        String usuario = System.getenv().getOrDefault("BD_PRUEBAS_USUARIO", "postgres");
        String clave = System.getenv().getOrDefault("PGPASSWORD", "");
        if (!comoApp) {
            return new DriverManagerDataSource(url(), usuario, clave);
        }
        try {
            SingleConnectionDataSource fuente = new SingleConnectionDataSource(url(), usuario, clave, true);
            try (Statement s = fuente.getConnection().createStatement()) {
                s.execute("SET ROLE app_ayudar");
            }
            return fuente;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public static synchronized void recrearUnaVez() {
        if (recreada) {
            return;
        }
        ejecutar("DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;");
        ejecutarArchivo(DB.resolve("db.sql"));
        for (Path sp : procedimientos()) {
            ejecutarArchivo(sp);
        }
        recreada = true;
    }

    public static synchronized void reiniciarDatos() {
        recrearUnaVez();
        List<String> tablas = admin().queryForList("""
                select tablename from pg_tables
                where schemaname = 'public' and tablename <> 'spatial_ref_sys'
                """, String.class);
        ejecutar("TRUNCATE " + String.join(", ", tablas) + " RESTART IDENTITY CASCADE;");
        ejecutarArchivo(DB.resolve("seed.sql"));
        ejecutarArchivo(DB.resolve("seed_dev.sql"));
    }

    public static List<Path> procedimientos() {
        try (Stream<Path> archivos = Files.list(DB.resolve("sp"))) {
            return archivos.filter(p -> p.toString().endsWith(".sql")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void ejecutarArchivo(Path archivo) {
        try {
            ejecutar(Files.readString(archivo, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Fallo al ejecutar " + archivo + ": " + e.getMessage(), e);
        }
    }

    private static void ejecutar(String sql) {
        admin().execute((Connection c) -> {
            try (Statement s = c.createStatement()) {
                s.execute(sql);
            }
            return null;
        });
    }
}
