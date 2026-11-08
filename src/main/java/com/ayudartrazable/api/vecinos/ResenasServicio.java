package com.ayudartrazable.api.vecinos;

import com.ayudartrazable.api.vecinos.dto.ResenasDto.PerfilVecinoPublico;
import com.ayudartrazable.api.vecinos.dto.ResenasDto.ResenaItem;
import com.ayudartrazable.api.vecinos.dto.ResenasDto.RespuestaVistoBueno;
import com.ayudartrazable.api.vecinos.dto.ResenasDto.SolicitudOcupacion;
import com.ayudartrazable.api.vecinos.dto.ResenasDto.SolicitudResena;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ResenasServicio {

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public ResenasServicio(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    private PerfilVecinoPublico mapPerfil(ResultSet rs, int rowNum) throws SQLException {
        return new PerfilVecinoPublico(
                rs.getString("id"),
                rs.getString("iniciales"),
                rs.getString("nombre"),
                rs.getString("oficio"),
                rs.getInt("anios_oficio"),
                rs.getString("distancia"),
                rs.getString("ubicacion"),
                rs.getInt("vistos_buenos"),
                rs.getDouble("calificacion"),
                rs.getInt("resenas_count"),
                rs.getString("fecha_registro"),
                rs.getBoolean("visto_bueno_dado")
        );
    }

    private ResenaItem mapResena(ResultSet rs, int rowNum) throws SQLException {
        List<String> destacados = List.of();
        try {
            String raw = rs.getString("destacados");
            if (raw != null && !raw.isBlank()) {
                destacados = json.readValue(raw, new TypeReference<List<String>>() {});
            }
        } catch (Exception ignored) {
        }
        return new ResenaItem(
                rs.getString("id"),
                rs.getString("autor"),
                rs.getString("iniciales"),
                rs.getString("ubicacion"),
                rs.getInt("calificacion"),
                rs.getBoolean("recomendo"),
                destacados,
                rs.getString("comentario"),
                rs.getString("fecha"),
                rs.getBoolean("verificado")
        );
    }

    public PerfilVecinoPublico obtenerPerfil(String vecinoId) {
        List<PerfilVecinoPublico> list = jdbc.query(
                "SELECT * FROM perfiles_vecinos WHERE id = ?",
                this::mapPerfil, vecinoId);
        if (!list.isEmpty()) {
            return list.get(0);
        }
        return new PerfilVecinoPublico(
                vecinoId, "V", "Vecino Verificado", "Servicio general", 5, "Cerca de ti",
                "Los Pinos", 10, 5.0, 3, "vecino verificado", false
        );
    }

    public RespuestaVistoBueno alternarVistoBueno(String vecinoId) {
        PerfilVecinoPublico actual = obtenerPerfil(vecinoId);
        boolean nuevoEstado = !actual.tieneMiVistoBueno();
        int nuevoTotal = Math.max(0, actual.vistosBuenos() + (nuevoEstado ? 1 : -1));

        int updated = jdbc.update("""
                UPDATE perfiles_vecinos
                SET visto_bueno_dado = ?, vistos_buenos = ?, actualizado_en = clock_timestamp()
                WHERE id = ?
                """, nuevoEstado, nuevoTotal, vecinoId);

        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO perfiles_vecinos (id, iniciales, nombre, oficio, anios_oficio, distancia, ubicacion, vistos_buenos, calificacion, resenas_count, fecha_registro, visto_bueno_dado)
                    VALUES (?, 'V', 'Vecino Verificado', 'Servicio general', 5, 'Cerca de ti', 'Los Pinos', ?, 5.0, 3, 'vecino verificado', ?)
                    """, vecinoId, nuevoTotal, nuevoEstado);
        }
        jdbc.update("UPDATE publicaciones_barrio SET vistos = ? WHERE id = ?", nuevoTotal, vecinoId);

        return new RespuestaVistoBueno(nuevoEstado, nuevoTotal);
    }

    public List<ResenaItem> listarResenas(String vecinoId) {
        return jdbc.query(
                "SELECT * FROM resenas_vecinos WHERE vecino_id = ? ORDER BY creado_en DESC",
                this::mapResena, vecinoId);
    }

    public ResenaItem agregarResena(String vecinoId, SolicitudResena solicitud, String autorNombre) {
        String id = UUID.randomUUID().toString();
        String autor = autorNombre != null && !autorNombre.isBlank() ? autorNombre : "Vecino Verificado";
        String iniciales = autor.substring(0, Math.min(2, autor.length())).toUpperCase();
        List<String> dest = solicitud.destacados() != null ? solicitud.destacados() : List.of();
        String destJson = "[]";
        try {
            destJson = json.writeValueAsString(dest);
        } catch (Exception ignored) {
        }

        jdbc.update("""
                INSERT INTO resenas_vecinos
                (id, vecino_id, autor, iniciales, ubicacion, calificacion, recomendo, destacados, comentario, fecha, verificado)
                VALUES (?, ?, ?, ?, 'Mz. D · a tu manzana', ?, ?, ?::jsonb, ?, 'Hoy', true)
                """,
                id, vecinoId, autor, iniciales, solicitud.calificacion(), solicitud.recomendo(), destJson, solicitud.comentario());

        if (solicitud.recomendo()) {
            jdbc.update("""
                    UPDATE perfiles_vecinos
                    SET visto_bueno_dado = true,
                        vistos_buenos = vistos_buenos + 1,
                        resenas_count = resenas_count + 1,
                        actualizado_en = clock_timestamp()
                    WHERE id = ?
                    """, vecinoId);
        } else {
            jdbc.update("""
                    UPDATE perfiles_vecinos
                    SET resenas_count = resenas_count + 1,
                        actualizado_en = clock_timestamp()
                    WHERE id = ?
                    """, vecinoId);
        }

        return new ResenaItem(
                id, autor, iniciales, "Mz. D · a tu manzana",
                solicitud.calificacion(), solicitud.recomendo(), dest, solicitud.comentario(), "Hoy", true
        );
    }

    public Map<String, Object> obtenerOcupacion(UUID usuarioId) {
        String key = usuarioId != null ? usuarioId.toString() : "default";
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT oficio, anios_experiencia, visible_directorio, activa FROM ocupaciones_usuario WHERE id = ?",
                (rs, i) -> Map.of(
                        "oficio", rs.getString("oficio") != null ? rs.getString("oficio") : "",
                        "aniosExperiencia", rs.getInt("anios_experiencia"),
                        "visibleDirectorio", rs.getBoolean("visible_directorio"),
                        "activa", rs.getBoolean("activa")
                ), key);
        if (!rows.isEmpty()) {
            return rows.get(0);
        }
        List<Map<String, Object>> defRows = jdbc.query(
                "SELECT oficio, anios_experiencia, visible_directorio, activa FROM ocupaciones_usuario WHERE id = 'default'",
                (rs, i) -> Map.of(
                        "oficio", rs.getString("oficio") != null ? rs.getString("oficio") : "",
                        "aniosExperiencia", rs.getInt("anios_experiencia"),
                        "visibleDirectorio", rs.getBoolean("visible_directorio"),
                        "activa", rs.getBoolean("activa")
                ));
        return defRows.isEmpty()
                ? Map.of("oficio", "Electricidad", "aniosExperiencia", 8, "visibleDirectorio", true, "activa", true)
                : defRows.get(0);
    }

    public boolean actualizarOcupacion(UUID usuarioId, SolicitudOcupacion ocupacion) {
        String key = usuarioId != null ? usuarioId.toString() : "default";
        String oficioLimpio = ocupacion.oficio() != null ? ocupacion.oficio().trim() : "";
        boolean activa = !oficioLimpio.isEmpty() && !oficioLimpio.equalsIgnoreCase("NINGUNO");

        jdbc.update("""
                INSERT INTO ocupaciones_usuario (id, usuario_id, oficio, anios_experiencia, visible_directorio, activa, actualizado_en)
                VALUES (?, ?, ?, ?, ?, ?, clock_timestamp())
                ON CONFLICT (id) DO UPDATE
                SET oficio = EXCLUDED.oficio,
                    anios_experiencia = EXCLUDED.anios_experiencia,
                    visible_directorio = EXCLUDED.visible_directorio,
                    activa = EXCLUDED.activa,
                    actualizado_en = clock_timestamp()
                """,
                key, usuarioId, activa ? oficioLimpio : "", ocupacion.aniosExperiencia(), ocupacion.visibleDirectorio(), activa);

        // Actualizar también el registro 'default' para sesiones sin token o pruebas rápidas
        jdbc.update("""
                INSERT INTO ocupaciones_usuario (id, usuario_id, oficio, anios_experiencia, visible_directorio, activa, actualizado_en)
                VALUES ('default', ?, ?, ?, ?, ?, clock_timestamp())
                ON CONFLICT (id) DO UPDATE
                SET oficio = EXCLUDED.oficio,
                    anios_experiencia = EXCLUDED.anios_experiencia,
                    visible_directorio = EXCLUDED.visible_directorio,
                    activa = EXCLUDED.activa,
                    actualizado_en = clock_timestamp()
                """,
                usuarioId, activa ? oficioLimpio : "", ocupacion.aniosExperiencia(), ocupacion.visibleDirectorio(), activa);

        if (activa) {
            jdbc.update("""
                    UPDATE actividad_vecinal
                    SET titulo = ?,
                        subtitulo = ?,
                        activo = true
                    WHERE id = 'u1'
                    """,
                    oficioLimpio + " · " + ocupacion.aniosExperiencia() + " años de experiencia",
                    ocupacion.visibleDirectorio()
                            ? "Visible en Servicios · vecinos pueden contactarte"
                            : "Oculto temporalmente del directorio");
        } else {
            jdbc.update("UPDATE actividad_vecinal SET activo = false WHERE id = 'u1'");
        }
        return true;
    }
}
