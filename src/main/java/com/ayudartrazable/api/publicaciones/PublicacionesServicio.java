package com.ayudartrazable.api.publicaciones;

import com.ayudartrazable.api.cuadrantes.dto.CuadrantesDto.PinMapa;
import com.ayudartrazable.api.publicaciones.dto.PublicacionesDto.CambioEstado;
import com.ayudartrazable.api.publicaciones.dto.PublicacionesDto.ItemActividad;
import com.ayudartrazable.api.publicaciones.dto.PublicacionesDto.PublicacionItem;
import com.ayudartrazable.api.publicaciones.dto.PublicacionesDto.RespuestaSolicitudFavor;
import com.ayudartrazable.api.publicaciones.dto.PublicacionesDto.SolicitudFavor;
import com.ayudartrazable.api.publicaciones.dto.PublicacionesDto.SolicitudPublicacion;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class PublicacionesServicio {

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public PublicacionesServicio(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    private PublicacionItem mapPublicacion(ResultSet rs, int rowNum) throws SQLException {
        List<String> condiciones = List.of();
        try {
            String raw = rs.getString("condiciones");
            if (raw != null && !raw.isBlank()) {
                condiciones = json.readValue(raw, new TypeReference<List<String>>() {});
            }
        } catch (Exception ignored) {
        }
        return new PublicacionItem(
                rs.getString("id"),
                rs.getString("iniciales"),
                rs.getString("nombre"),
                rs.getString("rubro"),
                rs.getString("detalle"),
                rs.getString("distancia"),
                rs.getInt("vistos"),
                rs.getDouble("calificacion"),
                rs.getString("avatar_bg"),
                rs.getString("avatar_color"),
                rs.getString("categoria"),
                rs.getString("tipo"),
                rs.getString("ubicacion"),
                rs.getString("metodo_contacto"),
                condiciones,
                rs.getString("tiempo")
        );
    }

    private ItemActividad mapActividad(ResultSet rs, int rowNum) throws SQLException {
        return new ItemActividad(
                rs.getString("id"),
                rs.getString("tipo"),
                rs.getString("titulo"),
                rs.getString("subtitulo"),
                rs.getString("estado"),
                rs.getString("fecha"),
                rs.getInt("interesados_count"),
                rs.getString("prestado_a"),
                rs.getString("hasta_fecha")
        );
    }

    public List<PublicacionItem> listar(String categoria, String tipo, String buscar) {
        StringBuilder sql = new StringBuilder("SELECT * FROM publicaciones_barrio WHERE activo = true");
        List<Object> params = new ArrayList<>();

        if (categoria != null && !categoria.isBlank()) {
            sql.append(" AND lower(categoria) = lower(?)");
            params.add(categoria);
        }
        if (tipo != null && !tipo.isBlank()) {
            sql.append(" AND lower(tipo) = lower(?)");
            params.add(tipo);
        }
        if (buscar != null && !buscar.isBlank()) {
            sql.append(" AND (lower(nombre) LIKE ? OR lower(rubro) LIKE ? OR lower(detalle) LIKE ?)");
            String pattern = "%" + buscar.toLowerCase() + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        sql.append(" ORDER BY creado_en DESC, id ASC");
        return jdbc.query(sql.toString(), this::mapPublicacion, params.toArray());
    }

    public PublicacionItem obtenerPorId(String id) {
        List<PublicacionItem> list = jdbc.query(
                "SELECT * FROM publicaciones_barrio WHERE id = ? AND activo = true",
                this::mapPublicacion, id);
        if (!list.isEmpty()) {
            return list.get(0);
        }
        List<PublicacionItem> fallback = jdbc.query(
                "SELECT * FROM publicaciones_barrio WHERE activo = true ORDER BY creado_en DESC LIMIT 1",
                this::mapPublicacion);
        return fallback.isEmpty() ? null : fallback.get(0);
    }

    public PublicacionItem crear(SolicitudPublicacion solicitud, String nombreAutor) {
        String id = UUID.randomUUID().toString();
        String cat = "servicio".equalsIgnoreCase(solicitud.tipo()) ? "servicios" : "favores";
        String iniciales = nombreAutor != null && !nombreAutor.isBlank()
                ? nombreAutor.substring(0, Math.min(2, nombreAutor.length())).toUpperCase()
                : "TU";
        String nombre = nombreAutor != null && !nombreAutor.isBlank() ? nombreAutor : "Tú";
        String detalle = solicitud.descripcion() != null && !solicitud.descripcion().isBlank()
                ? solicitud.descripcion()
                : "Publicado en tu cuadrante";
        String bg = "servicios".equals(cat) ? "#d6eee2" : "#e0e7fb";
        String color = "servicios".equals(cat) ? "#144e40" : "#2b4290";
        String tipoNorm = solicitud.tipo().toLowerCase();
        String ubicacion = solicitud.ubicacion() != null && !solicitud.ubicacion().isBlank()
                ? solicitud.ubicacion()
                : "Mz. D, Los Pinos";
        String contacto = solicitud.metodoContacto() != null && !solicitud.metodoContacto().isBlank()
                ? solicitud.metodoContacto()
                : "chat";
        List<String> conds = solicitud.condiciones() != null ? solicitud.condiciones() : List.of("Coordinar por chat");
        String condsJson = "[]";
        try {
            condsJson = json.writeValueAsString(conds);
        } catch (Exception ignored) {
        }

        jdbc.update("""
                INSERT INTO publicaciones_barrio
                (id, iniciales, nombre, rubro, detalle, distancia, vistos, calificacion, avatar_bg, avatar_color,
                 categoria, tipo, ubicacion, metodo_contacto, condiciones, tiempo, latitud, longitud, tag, estado, activo)
                VALUES (?, ?, ?, ?, ?, '0 m (Tu casa)', 1, 5.0, ?, ?, ?, ?, ?, ?, ?::jsonb, 'Recién publicado', -12.0905, -76.9902, 'Todos', 'Activo', true)
                """,
                id, iniciales, nombre, solicitud.titulo(), detalle, bg, color, cat, tipoNorm, ubicacion, contacto, condsJson);

        String badge = "servicio".equalsIgnoreCase(tipoNorm)
                ? "Mi servicio"
                : tipoNorm.substring(0, 1).toUpperCase() + tipoNorm.substring(1);

        jdbc.update("""
                INSERT INTO actividad_vecinal
                (id, lista_tipo, tipo, titulo, subtitulo, estado, fecha, interesados_count, prestado_a, hasta_fecha, activo)
                VALUES (?, 'MIS_PUBLICACIONES', ?, ?, ?, 'Activo', 'Hoy', 0, NULL, NULL, true)
                """,
                id, badge, solicitud.titulo(), detalle);

        return new PublicacionItem(
                id, iniciales, nombre, solicitud.titulo(), detalle, "0 m (Tu casa)",
                1, 5.0, bg, color, cat, tipoNorm, ubicacion, contacto, conds, "Recién publicado"
        );
    }

    public RespuestaSolicitudFavor solicitarFavor(String id, SolicitudFavor solicitud, String solicitante) {
        PublicacionItem item = obtenerPorId(id);
        String favorId = UUID.randomUUID().toString();
        String titulo = item != null ? item.rubro() : "Favor vecinal";
        String propietario = item != null ? item.nombre() : "Vecino";
        String plazo = (solicitud != null && solicitud.fechaDevolucion() != null && !solicitud.fechaDevolucion().isBlank())
                ? solicitud.fechaDevolucion()
                : "esta semana";

        jdbc.update("""
                INSERT INTO actividad_vecinal
                (id, lista_tipo, tipo, titulo, subtitulo, estado, fecha, interesados_count, prestado_a, hasta_fecha, activo)
                VALUES (?, 'PEDI', 'Pedí', ?, ?, 'En curso', 'Hoy', 1, ?, ?, true)
                """,
                favorId, titulo, "Solicitado a " + propietario + " · Por devolver " + plazo, propietario, plazo);

        return new RespuestaSolicitudFavor(
                true,
                "Solicitud de préstamo enviada al vecino " + propietario,
                favorId,
                "2"
        );
    }

    public boolean cambiarEstado(String id, CambioEstado cambio) {
        jdbc.update("UPDATE publicaciones_barrio SET estado = ? WHERE id = ?", cambio.estado(), id);
        jdbc.update("UPDATE actividad_vecinal SET estado = ? WHERE id = ?", cambio.estado(), id);
        return true;
    }

    public boolean eliminar(String id) {
        jdbc.update("UPDATE publicaciones_barrio SET activo = false WHERE id = ?", id);
        jdbc.update("UPDATE actividad_vecinal SET activo = false WHERE id = ?", id);
        return true;
    }

    public List<ItemActividad> listarMisPublicaciones() {
        return jdbc.query(
                "SELECT * FROM actividad_vecinal WHERE lista_tipo = 'MIS_PUBLICACIONES' AND activo = true ORDER BY creado_en DESC, id ASC",
                this::mapActividad);
    }

    public List<ItemActividad> listarFavoresHice() {
        return jdbc.query(
                "SELECT * FROM actividad_vecinal WHERE lista_tipo = 'HICE' AND activo = true ORDER BY creado_en DESC, id ASC",
                this::mapActividad);
    }

    public List<ItemActividad> listarFavoresPedi() {
        return jdbc.query(
                "SELECT * FROM actividad_vecinal WHERE lista_tipo = 'PEDI' AND activo = true ORDER BY creado_en DESC, id ASC",
                this::mapActividad);
    }

    public boolean confirmarDevolucion(String id) {
        jdbc.update("""
                UPDATE actividad_vecinal
                SET estado = 'Finalizado',
                    subtitulo = 'Devolución confirmada con éxito. Ya puedes calificar a tu vecino.'
                WHERE id = ?
                """, id);
        return true;
    }

    public List<PinMapa> listarPinesMapa(String categoria, String tag) {
        StringBuilder sql = new StringBuilder("SELECT * FROM publicaciones_barrio WHERE activo = true");
        List<Object> params = new ArrayList<>();
        if (categoria != null && !categoria.isBlank()) {
            sql.append(" AND lower(categoria) = lower(?)");
            params.add(categoria);
        }
        if (tag != null && !tag.isBlank() && !tag.equalsIgnoreCase("Todos")) {
            sql.append(" AND (lower(tag) = lower(?) OR lower(rubro) LIKE ?)");
            params.add(tag);
            params.add("%" + tag.toLowerCase() + "%");
        }
        sql.append(" ORDER BY creado_en DESC, id ASC");
        return jdbc.query(sql.toString(), (rs, i) -> new PinMapa(
                rs.getString("id"),
                rs.getString("iniciales"),
                rs.getString("nombre"),
                rs.getString("rubro"),
                rs.getInt("vistos"),
                rs.getString("distancia"),
                "3 min a pie",
                rs.getDouble("latitud"),
                rs.getDouble("longitud"),
                rs.getString("categoria"),
                rs.getString("tag")
        ), params.toArray());
    }
}
