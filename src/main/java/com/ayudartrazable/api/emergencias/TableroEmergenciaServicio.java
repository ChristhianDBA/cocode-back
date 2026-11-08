package com.ayudartrazable.api.emergencias;

import com.ayudartrazable.api.emergencias.dto.TableroEmergenciaDto.AporteItem;
import com.ayudartrazable.api.emergencias.dto.TableroEmergenciaDto.DirectorioLocalResponse;
import com.ayudartrazable.api.emergencias.dto.TableroEmergenciaDto.EstadoTableroResponse;
import com.ayudartrazable.api.emergencias.dto.TableroEmergenciaDto.NecesidadItem;
import com.ayudartrazable.api.emergencias.dto.TableroEmergenciaDto.RespuestaConfirmacion;
import com.ayudartrazable.api.emergencias.dto.TableroEmergenciaDto.ServicioLocalItem;
import com.ayudartrazable.api.emergencias.dto.TableroEmergenciaDto.SolicitudActivarTablero;
import com.ayudartrazable.api.emergencias.dto.TableroEmergenciaDto.SolicitudAporte;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

@Service
public class TableroEmergenciaServicio {

    private final List<ServicioLocalItem> directorioLocal = new CopyOnWriteArrayList<>();
    private final List<NecesidadItem> necesidades = new CopyOnWriteArrayList<>();
    private final List<AporteItem> aportes = new CopyOnWriteArrayList<>();
    private final AtomicInteger personasASalvo = new AtomicInteger(126);
    private final AtomicInteger confirmaciones = new AtomicInteger(5);
    private volatile boolean tableroActivo = true;
    private volatile String tipoEmergenciaActual = "Lluvias intensas en Los Pinos";
    private volatile String descripcionActual = "Activado hoy 06:40 · confirmado por 5 vecinos en 12 min";

    public TableroEmergenciaServicio() {
        // Semillas oficiales Figma Screen 8 "En tu barrio"
        directorioLocal.add(new ServicioLocalItem("1", "SZ", "Serenazgo de Surco", "[Nº SERENAZGO] · 24 h", "0.5 km", "014115555", "#faecd5", "#8c531b"));
        directorioLocal.add(new ServicioLocalItem("2", "CM", "Comisaría del sector", "1.2 km · [Nº COMISARÍA]", "1.2 km", "012711000", "#faecd5", "#8c531b"));
        directorioLocal.add(new ServicioLocalItem("3", "PS", "Posta de salud", "800 m · Abierta hasta 20:00", "800 m", "014490000", "#fee4e2", "#912018"));
        directorioLocal.add(new ServicioLocalItem("4", "FT", "Farmacia de turno", "350 m · Abierta ahora", "350 m", "013142020", "#d6eee2", "#144e40"));
        directorioLocal.add(new ServicioLocalItem("5", "CJ", "Cerrajería 24 h · Ana Paredes", "Servicio verificado", "400 m", "987654321", "#faecd5", "#8c531b"));

        // Semillas de ayuda comunitaria
        necesidades.add(new NecesidadItem("n1", "Agua embotellada", "Prioridad alta", 120, 300, "L", false));
        necesidades.add(new NecesidadItem("n2", "Frazadas", "Prioridad alta", 45, 80, "", false));
        necesidades.add(new NecesidadItem("n3", "Botiquín básico", "Prioridad alta", 6, 20, "", false));
        necesidades.add(new NecesidadItem("n4", "Voluntarios para limpieza", "Prioridad alta", 8, 15, "personas", false));

        aportes.add(new AporteItem("a1", "10 Bidones de agua mineral", "Julio R. · Mz. A", "Hace 15 min", false));
        aportes.add(new AporteItem("a2", "4 Frazadas y ropa abrigadora", "Rosa Q. · Mz. C", "Hace 25 min", false));
        aportes.add(new AporteItem("a3", "Botiquín con gasas y alcohol", "Lucía T. · Mz. B", "Hace 40 min", false));
    }

    public DirectorioLocalResponse obtenerDirectorioLocal(String cuadrante) {
        return new DirectorioLocalResponse(
                "Santiago de Surco",
                cuadrante != null ? cuadrante : "Urb. Los Pinos, Surco",
                new ArrayList<>(directorioLocal)
        );
    }

    public EstadoTableroResponse obtenerEstadoTablero(String cuadrante) {
        return new EstadoTableroResponse(
                "tablero-1",
                cuadrante != null ? cuadrante : "Urb. Los Pinos, Surco",
                tipoEmergenciaActual,
                descripcionActual,
                tableroActivo ? "ACTIVO" : "SOLICITADO",
                confirmaciones.get(),
                5,
                tableroActivo ? 0 : 25,
                tableroActivo,
                personasASalvo.get()
        );
    }

    public EstadoTableroResponse solicitarActivacion(SolicitudActivarTablero solicitud) {
        tipoEmergenciaActual = solicitud.tipoEmergencia();
        descripcionActual = solicitud.descripcion();
        confirmaciones.set(1);
        tableroActivo = false;

        return new EstadoTableroResponse(
                "tablero-1",
                solicitud.cuadrante() != null ? solicitud.cuadrante() : "Urb. Los Pinos, Surco",
                solicitud.tipoEmergencia(),
                solicitud.descripcion(),
                "SOLICITADO",
                1,
                5,
                30,
                false,
                personasASalvo.get()
        );
    }

    public RespuestaConfirmacion confirmarSolicitud(String id) {
        int c = confirmaciones.incrementAndGet();
        boolean activado = c >= 5;
        if (activado) {
            tableroActivo = true;
        }
        return new RespuestaConfirmacion(
                true,
                c,
                activado,
                activado ? "¡Tablero activado para todo el barrio!" : "Confirmación registrada (" + c + "/5)"
        );
    }

    public int registrarEstoyASalvo(String id) {
        return personasASalvo.incrementAndGet();
    }

    public List<NecesidadItem> listarNecesidades(String id) {
        return new ArrayList<>(necesidades);
    }

    public AporteItem registrarAporte(String id, SolicitudAporte solicitud, String vecino) {
        AporteItem nuevo = new AporteItem(
                UUID.randomUUID().toString(),
                solicitud.descripcion(),
                vecino != null ? vecino : "Vecino Verificado",
                "Ahora",
                false
        );
        aportes.add(0, nuevo);
        return nuevo;
    }
}
