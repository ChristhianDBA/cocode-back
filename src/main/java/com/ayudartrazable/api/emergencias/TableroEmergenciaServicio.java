package com.ayudartrazable.api.emergencias;

import com.ayudartrazable.api.emergencias.dto.TableroEmergenciaDto.AporteItem;
import com.ayudartrazable.api.emergencias.dto.TableroEmergenciaDto.DirectorioLocalResponse;
import com.ayudartrazable.api.emergencias.dto.TableroEmergenciaDto.EstadoTableroResponse;
import com.ayudartrazable.api.emergencias.dto.TableroEmergenciaDto.NecesidadItem;
import com.ayudartrazable.api.emergencias.dto.TableroEmergenciaDto.RespuestaConfirmacion;
import com.ayudartrazable.api.emergencias.dto.TableroEmergenciaDto.ServicioLocalItem;
import com.ayudartrazable.api.emergencias.dto.TableroEmergenciaDto.SolicitudActivarTablero;
import com.ayudartrazable.api.emergencias.dto.TableroEmergenciaDto.SolicitudAporte;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TableroEmergenciaServicio {

    private final JdbcTemplate jdbc;

    public TableroEmergenciaServicio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public DirectorioLocalResponse obtenerDirectorioLocal(String cuadrante) {
        List<ServicioLocalItem> servicios = jdbc.query(
                "SELECT * FROM directorio_local ORDER BY orden ASC, id ASC",
                (rs, i) -> new ServicioLocalItem(
                        rs.getString("id"),
                        rs.getString("initials"),
                        rs.getString("name"),
                        rs.getString("subtitle"),
                        rs.getString("distance"),
                        rs.getString("phone"),
                        rs.getString("bg"),
                        rs.getString("color")
                ));
        return new DirectorioLocalResponse(
                "Lima",
                cuadrante != null && !cuadrante.isBlank() ? cuadrante : "Tu cuadrante vecinal",
                servicios
        );
    }

    public EstadoTableroResponse obtenerEstadoTablero(String cuadrante) {
        List<EstadoTableroResponse> list = jdbc.query(
                "SELECT * FROM tablero_emergencia WHERE id = 'tablero-1'",
                (rs, i) -> new EstadoTableroResponse(
                        rs.getString("id"),
                        cuadrante != null && !cuadrante.isBlank() ? cuadrante : rs.getString("cuadrante"),
                        rs.getString("tipo_emergencia"),
                        rs.getString("descripcion"),
                        rs.getString("estado"),
                        rs.getInt("confirmaciones"),
                        rs.getInt("meta_confirmaciones"),
                        rs.getInt("minutos_restantes"),
                        rs.getBoolean("activo"),
                        rs.getInt("personas_a_salvo")
                ));
        if (!list.isEmpty()) {
            return list.get(0);
        }
        return new EstadoTableroResponse(
                "tablero-1",
                cuadrante != null ? cuadrante : "Tu cuadrante vecinal",
                "Lluvias intensas",
                "Activado hoy · confirmado por vecinos",
                "ACTIVO", 5, 5, 0, true, 126
        );
    }

    public EstadoTableroResponse solicitarActivacion(SolicitudActivarTablero solicitud) {
        String cuad = solicitud.cuadrante() != null && !solicitud.cuadrante().isBlank()
                ? solicitud.cuadrante()
                : "Tu cuadrante vecinal";

        jdbc.update("""
                UPDATE tablero_emergencia
                SET cuadrante = ?,
                    tipo_emergencia = ?,
                    descripcion = ?,
                    estado = 'SOLICITADO',
                    confirmaciones = 1,
                    minutos_restantes = 30,
                    activo = false,
                    actualizado_en = clock_timestamp()
                WHERE id = 'tablero-1'
                """, cuad, solicitud.tipoEmergencia(), solicitud.descripcion());

        return obtenerEstadoTablero(cuad);
    }

    public RespuestaConfirmacion confirmarSolicitud(String id) {
        jdbc.update("""
                UPDATE tablero_emergencia
                SET confirmaciones = confirmaciones + 1,
                    activo = (confirmaciones + 1) >= meta_confirmaciones,
                    estado = CASE WHEN (confirmaciones + 1) >= meta_confirmaciones THEN 'ACTIVO' ELSE estado END,
                    minutos_restantes = CASE WHEN (confirmaciones + 1) >= meta_confirmaciones THEN 0 ELSE minutos_restantes END,
                    actualizado_en = clock_timestamp()
                WHERE id = 'tablero-1'
                """);

        EstadoTableroResponse est = obtenerEstadoTablero(null);
        boolean activado = est.activo();
        int c = est.confirmacionesActuales();

        return new RespuestaConfirmacion(
                true,
                c,
                activado,
                activado ? "¡Tablero activado para todo el barrio!" : "Confirmación registrada (" + c + "/5)"
        );
    }

    public int registrarEstoyASalvo(String id) {
        jdbc.update("""
                UPDATE tablero_emergencia
                SET personas_a_salvo = personas_a_salvo + 1,
                    actualizado_en = clock_timestamp()
                WHERE id = 'tablero-1'
                """);
        return obtenerEstadoTablero(null).personasASalvoCount();
    }

    public List<NecesidadItem> listarNecesidades(String id) {
        return jdbc.query(
                "SELECT * FROM tablero_necesidades WHERE tablero_id = 'tablero-1' ORDER BY orden ASC, id ASC",
                (rs, i) -> new NecesidadItem(
                        rs.getString("id"),
                        rs.getString("recurso"),
                        rs.getString("prioridad"),
                        rs.getInt("actual"),
                        rs.getInt("meta"),
                        rs.getString("unidad"),
                        rs.getBoolean("cubierto")
                ));
    }

    public AporteItem registrarAporte(String id, SolicitudAporte solicitud, String vecino) {
        String aporteId = UUID.randomUUID().toString();
        String autor = vecino != null && !vecino.isBlank() ? vecino : "Vecino Verificado";

        jdbc.update("""
                INSERT INTO tablero_aportes (id, tablero_id, descripcion, vecino, tiempo, entregado)
                VALUES (?, 'tablero-1', ?, ?, 'Ahora', false)
                """, aporteId, solicitud.descripcion(), autor);

        if (solicitud.necesidadId() != null && !solicitud.necesidadId().isBlank()) {
            int cant = Math.max(1, solicitud.cantidad());
            jdbc.update("""
                    UPDATE tablero_necesidades
                    SET actual = actual + ?,
                        cubierto = (actual + ?) >= meta
                    WHERE id = ?
                    """, cant, cant, solicitud.necesidadId());
        }

        return new AporteItem(aporteId, solicitud.descripcion(), autor, "Ahora", false);
    }
}
