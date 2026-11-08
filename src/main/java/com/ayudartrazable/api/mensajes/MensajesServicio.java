package com.ayudartrazable.api.mensajes;

import com.ayudartrazable.api.mensajes.dto.MensajesDto.ConversacionItem;
import com.ayudartrazable.api.mensajes.dto.MensajesDto.MensajeBubble;
import com.ayudartrazable.api.mensajes.dto.MensajesDto.ReporteUsuario;
import com.ayudartrazable.api.mensajes.dto.MensajesDto.SolicitudEnviarMensaje;
import com.ayudartrazable.api.mensajes.dto.MensajesDto.SolicitudIniciarChat;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MensajesServicio {

    private final JdbcTemplate jdbc;

    public MensajesServicio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private ConversacionItem mapConversacion(ResultSet rs, int rowNum) throws SQLException {
        return new ConversacionItem(
                rs.getString("id"),
                rs.getString("avatar_initials"),
                rs.getString("avatar_bg"),
                rs.getString("avatar_color"),
                rs.getString("name"),
                rs.getString("subtitle"),
                rs.getString("last_message"),
                rs.getString("time_label"),
                rs.getInt("unread"),
                rs.getBoolean("is_official")
        );
    }

    private MensajeBubble mapMensaje(ResultSet rs, int rowNum) throws SQLException {
        return new MensajeBubble(
                rs.getString("id"),
                rs.getString("conversacion_id"),
                rs.getString("texto"),
                rs.getString("sender"),
                rs.getString("hora"),
                rs.getBoolean("verificado")
        );
    }

    public List<ConversacionItem> listar(String filtro, String buscar) {
        StringBuilder sql = new StringBuilder("SELECT * FROM chat_conversaciones WHERE bloqueado = false");
        List<Object> params = new ArrayList<>();

        if (filtro != null && !filtro.equalsIgnoreCase("Todos") && !filtro.isBlank()) {
            if (filtro.equalsIgnoreCase("Servicios")) {
                sql.append(" AND lower(subtitle) LIKE '%servicio%'");
            } else if (filtro.equalsIgnoreCase("Favores")) {
                sql.append(" AND lower(subtitle) LIKE '%favor%'");
            } else if (filtro.equalsIgnoreCase("Grupos")) {
                sql.append(" AND (lower(subtitle) LIKE '%grupo%' OR is_official = true)");
            }
        }
        if (buscar != null && !buscar.isBlank()) {
            sql.append(" AND (lower(name) LIKE ? OR lower(subtitle) LIKE ?)");
            String p = "%" + buscar.toLowerCase() + "%";
            params.add(p);
            params.add(p);
        }
        sql.append(" ORDER BY actualizado_en DESC, id ASC");
        return jdbc.query(sql.toString(), this::mapConversacion, params.toArray());
    }

    public List<MensajeBubble> obtenerMensajes(String conversacionId) {
        return jdbc.query(
                "SELECT * FROM chat_mensajes WHERE conversacion_id = ? ORDER BY creado_en ASC, id ASC",
                this::mapMensaje, conversacionId);
    }

    public MensajeBubble enviarMensaje(String conversacionId, SolicitudEnviarMensaje solicitud) {
        String id = UUID.randomUUID().toString();
        String hora = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));

        jdbc.update("""
                INSERT INTO chat_mensajes (id, conversacion_id, texto, sender, hora, verificado)
                VALUES (?, ?, ?, 'yo', ?, true)
                """, id, conversacionId, solicitud.texto(), hora);

        jdbc.update("""
                UPDATE chat_conversaciones
                SET last_message = ?, time_label = ?, unread = 0, actualizado_en = clock_timestamp()
                WHERE id = ?
                """, "Tú: " + solicitud.texto(), hora, conversacionId);

        return new MensajeBubble(id, conversacionId, solicitud.texto(), "yo", hora, true);
    }

    public ConversacionItem iniciarChat(SolicitudIniciarChat solicitud) {
        List<ConversacionItem> existentes = jdbc.query(
                "SELECT * FROM chat_conversaciones WHERE id = ?",
                this::mapConversacion, solicitud.vecinoId());
        if (!existentes.isEmpty()) {
            if (solicitud.mensajeInicial() != null && !solicitud.mensajeInicial().isBlank()) {
                enviarMensaje(solicitud.vecinoId(), new SolicitudEnviarMensaje(solicitud.mensajeInicial()));
            }
            return existentes.get(0);
        }

        String msg = solicitud.mensajeInicial() != null ? solicitud.mensajeInicial() : "Chat iniciado";
        jdbc.update("""
                INSERT INTO chat_conversaciones
                (id, avatar_initials, avatar_bg, avatar_color, name, subtitle, last_message, time_label, unread, is_official)
                VALUES (?, 'V', '#e0e7fb', '#2b4290', 'Vecino Coordinador', 'Coordinación vecinal', ?, 'Ahora', 0, false)
                ON CONFLICT (id) DO NOTHING
                """, solicitud.vecinoId(), msg);

        if (solicitud.mensajeInicial() != null && !solicitud.mensajeInicial().isBlank()) {
            enviarMensaje(solicitud.vecinoId(), new SolicitudEnviarMensaje(solicitud.mensajeInicial()));
        }

        return new ConversacionItem(
                solicitud.vecinoId(), "V", "#e0e7fb", "#2b4290",
                "Vecino Coordinador", "Coordinación vecinal", msg, "Ahora", 0, false
        );
    }

    public boolean marcarLeido(String conversacionId) {
        jdbc.update("UPDATE chat_conversaciones SET unread = 0 WHERE id = ?", conversacionId);
        return true;
    }

    public boolean bloquearUsuario(String usuarioId) {
        jdbc.update("UPDATE chat_conversaciones SET bloqueado = true WHERE id = ?", usuarioId);
        return true;
    }

    public boolean reportarUsuario(String usuarioId, ReporteUsuario reporte) {
        jdbc.update("""
                INSERT INTO chat_reportes (id, usuario_id, motivo, detalle)
                VALUES (?, ?, ?, NULL)
                """, UUID.randomUUID().toString(), usuarioId, reporte.motivo());
        return true;
    }
}
