package com.ayudartrazable.api.publicaciones;

import com.ayudartrazable.api.publicaciones.dto.PublicacionesDto.CambioEstado;
import com.ayudartrazable.api.publicaciones.dto.PublicacionesDto.ItemActividad;
import com.ayudartrazable.api.publicaciones.dto.PublicacionesDto.PublicacionItem;
import com.ayudartrazable.api.publicaciones.dto.PublicacionesDto.RespuestaSolicitudFavor;
import com.ayudartrazable.api.publicaciones.dto.PublicacionesDto.SolicitudFavor;
import com.ayudartrazable.api.publicaciones.dto.PublicacionesDto.SolicitudPublicacion;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;

@Service
public class PublicacionesServicio {

    private final List<PublicacionItem> publicaciones = new CopyOnWriteArrayList<>();
    private final List<ItemActividad> publicacionesUsuario = new CopyOnWriteArrayList<>();
    private final List<ItemActividad> favoresHice = new CopyOnWriteArrayList<>();
    private final List<ItemActividad> favoresPedi = new CopyOnWriteArrayList<>();
    private final Map<String, String> estados = new ConcurrentHashMap<>();

    public PublicacionesServicio() {
        // Inicializar datos semilla oficiales alineados al diseño de Figma
        publicaciones.add(new PublicacionItem(
                "1", "RQ", "Rosa Quispe", "Gasfitería", "Atiende hoy", "180 m", 42, 4.9,
                "#d6eee2", "#144e40", "servicios", "servicio", "Mz. D, Los Pinos", "chat",
                List.of("12 años de experiencia", "Garantía de servicio"), "Hace 2 horas"));

        publicaciones.add(new PublicacionItem(
                "2", "MH", "Miguel Huamán", "Electricista domiciliario", "Instalaciones y emergencias", "350 m", 31, 4.8,
                "#faecd5", "#8c531b", "servicios", "servicio", "Mz. B, Los Pinos", "chat",
                List.of("Técnico certificado", "Disponibilidad fines de semana"), "Hace 4 horas"));

        publicaciones.add(new PublicacionItem(
                "3", "LT", "Lucía Torres", "Clases de matemática", "Secundaria y preuniversitario", "420 m", 27, 5.0,
                "#e0e7fb", "#2b4290", "servicios", "servicio", "Mz. C, Los Pinos", "chat",
                List.of("Modalidad presencial o virtual"), "Ayer"));

        publicaciones.add(new PublicacionItem(
                "4", "JC", "Jorge Córdova", "Carpintería y cerrajería", "Duplicado de llaves y chapas", "600 m", 19, 4.7,
                "#fee4e2", "#912018", "servicios", "servicio", "Mz. A, Los Pinos", "whatsapp",
                List.of("Atención de emergencias 24h"), "Ayer"));

        publicaciones.add(new PublicacionItem(
                "5", "JR", "Julio R. · Mz. A", "Presto taladro percutor", "Para el fin de semana. Solo pido que lo devuelvas limpio.", "150 m", 23, 5.0,
                "#e0e7fb", "#2b4290", "favores", "presto", "Mz. A, Los Pinos", "chat",
                List.of("Disponible: fin de semana", "Préstamo máximo: 3 días"), "Hace 3 días"));

        publicaciones.add(new PublicacionItem(
                "6", "TL", "Teresa L.", "Presto escalera telescópica", "Para trabajos en casa hasta 3 metros", "310 m", 15, 4.9,
                "#fee4e2", "#912018", "favores", "presto", "Mz. B, Los Pinos", "chat",
                List.of("Préstamo máximo: 2 días"), "Hace 4 días"));

        publicaciones.add(new PublicacionItem(
                "7", "CR", "Carlos Ramos", "Dono libros escolares de primaria", "3ro y 4to de primaria en buen estado", "390 m", 22, 4.9,
                "#d6eee2", "#144e40", "favores", "dono", "Mz. C, Los Pinos", "chat",
                List.of("Entrega en portería"), "Hace 5 días"));

        // Semillas para la pestaña "Mi Actividad" (Figma Screen 4)
        publicacionesUsuario.add(new ItemActividad(
                "u1", "Mi servicio", "Electricidad · 8 años de experiencia",
                "Visible en Servicios · 4 vecinos te contactaron esta semana", "Activo", "Hoy", 4, null, null));
        publicacionesUsuario.add(new ItemActividad(
                "u2", "Presto", "Presto escalera de 3 metros",
                "Prestada a Teresa L. hasta el domingo", "En curso", "Hace 2 días", 1, "Teresa L.", "domingo"));
        publicacionesUsuario.add(new ItemActividad(
                "u3", "Dono", "Dono libros escolares de primaria",
                "Publicado hace 3 días · 2 vecinos interesados", "Activo", "Hace 3 días", 2, null, null));

        favoresHice.add(new ItemActividad(
                "h1", "Presto", "Presto escalera de 3 metros",
                "Prestada a Teresa L. hasta el domingo", "En curso", "Esta semana", 1, "Teresa L.", "domingo"));
        favoresHice.add(new ItemActividad(
                "h2", "Ofrezco", "Ayuda podando ramas del pasaje B",
                "Completado con Jorge C.", "Finalizado", "Hace 2 semanas", 1, "Jorge C.", null));

        favoresPedi.add(new ItemActividad(
                "p1", "Pedí", "Taladro percutor",
                "Prestado por Julio R. · Por devolver el lunes", "En curso", "Ayer", 1, "Julio R.", "lunes"));
    }

    public List<PublicacionItem> listar(String categoria, String tipo, String buscar) {
        return publicaciones.stream()
                .filter(p -> categoria == null || categoria.isBlank() || p.categoria().equalsIgnoreCase(categoria))
                .filter(p -> tipo == null || tipo.isBlank() || p.tipo().equalsIgnoreCase(tipo))
                .filter(p -> {
                    if (buscar == null || buscar.isBlank()) return true;
                    String b = buscar.toLowerCase();
                    return p.nombre().toLowerCase().contains(b) ||
                           p.rubro().toLowerCase().contains(b) ||
                           p.detalle().toLowerCase().contains(b);
                })
                .toList();
    }

    public PublicacionItem obtenerPorId(String id) {
        return publicaciones.stream()
                .filter(p -> p.id().equals(id))
                .findFirst()
                .orElse(publicaciones.isEmpty() ? null : publicaciones.get(0));
    }

    public PublicacionItem crear(SolicitudPublicacion solicitud, String nombreAutor) {
        String id = UUID.randomUUID().toString();
        String cat = "servicio".equalsIgnoreCase(solicitud.tipo()) ? "servicios" : "favores";
        String iniciales = nombreAutor != null && !nombreAutor.isBlank()
                ? nombreAutor.substring(0, Math.min(2, nombreAutor.length())).toUpperCase()
                : "TU";

        PublicacionItem nueva = new PublicacionItem(
                id,
                iniciales,
                nombreAutor != null ? nombreAutor : "Tú",
                solicitud.titulo(),
                solicitud.descripcion() != null ? solicitud.descripcion() : "Publicado en tu cuadrante",
                "0 m (Tu casa)",
                1,
                5.0,
                "servicios".equals(cat) ? "#d6eee2" : "#e0e7fb",
                "servicios".equals(cat) ? "#144e40" : "#2b4290",
                cat,
                solicitud.tipo().toLowerCase(),
                solicitud.ubicacion() != null ? solicitud.ubicacion() : "Mz. D, Los Pinos",
                solicitud.metodoContacto() != null ? solicitud.metodoContacto() : "chat",
                solicitud.condiciones() != null ? solicitud.condiciones() : List.of("Coordinar por chat"),
                "Recién publicado"
        );

        publicaciones.add(0, nueva);

        // También registrar en Mi Actividad
        publicacionesUsuario.add(0, new ItemActividad(
                nueva.id(),
                "servicio".equalsIgnoreCase(nueva.tipo()) ? "Mi servicio" : nueva.tipo().substring(0, 1).toUpperCase() + nueva.tipo().substring(1),
                nueva.rubro(),
                nueva.detalle(),
                "Activo",
                "Hoy",
                0,
                null,
                null
        ));

        return nueva;
    }

    public RespuestaSolicitudFavor solicitarFavor(String id, SolicitudFavor solicitud, String solicitante) {
        PublicacionItem item = obtenerPorId(id);
        String chatSugerido = "2"; // ID chat Julio R.
        return new RespuestaSolicitudFavor(
                true,
                "Solicitud de préstamo enviada al vecino " + (item != null ? item.nombre() : "propietario"),
                UUID.randomUUID().toString(),
                chatSugerido
        );
    }

    public boolean cambiarEstado(String id, CambioEstado cambio) {
        estados.put(id, cambio.estado());
        return true;
    }

    public List<ItemActividad> listarMisPublicaciones() {
        return new ArrayList<>(publicacionesUsuario);
    }

    public List<ItemActividad> listarFavoresHice() {
        return new ArrayList<>(favoresHice);
    }

    public List<ItemActividad> listarFavoresPedi() {
        return new ArrayList<>(favoresPedi);
    }

    public boolean confirmarDevolucion(String id) {
        for (int i = 0; i < publicacionesUsuario.size(); i++) {
            ItemActividad it = publicacionesUsuario.get(i);
            if (it.id().equals(id)) {
                publicacionesUsuario.set(i, new ItemActividad(
                        it.id(), it.tipo(), it.titulo(), "Devolución confirmada con éxito. Ya puedes calificar a tu vecino.",
                        "Finalizado", it.fecha(), it.interesadosCount(), it.prestadoA(), it.hastaFecha()
                ));
                return true;
            }
        }
        return true;
    }
}
