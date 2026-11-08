package com.ayudartrazable.api.mensajes;

import com.ayudartrazable.api.mensajes.dto.MensajesDto.ConversacionItem;
import com.ayudartrazable.api.mensajes.dto.MensajesDto.MensajeBubble;
import com.ayudartrazable.api.mensajes.dto.MensajesDto.ReporteUsuario;
import com.ayudartrazable.api.mensajes.dto.MensajesDto.SolicitudEnviarMensaje;
import com.ayudartrazable.api.mensajes.dto.MensajesDto.SolicitudIniciarChat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;

@Service
public class MensajesServicio {

    private final List<ConversacionItem> conversaciones = new CopyOnWriteArrayList<>();
    private final Map<String, List<MensajeBubble>> hilos = new ConcurrentHashMap<>();

    public MensajesServicio() {
        // Semillas oficiales de Figma Mensajes
        conversaciones.add(new ConversacionItem(
                "1", "RQ", "#d6eee2", "#144e40", "Rosa Quispe", "Servicio · Gasfitería",
                "Puedo pasar mañana a las 9, ¿le ...", "23:05", 2, false));

        conversaciones.add(new ConversacionItem(
                "2", "JR", "#e0e7fb", "#2b4290", "Julio R.", "Favor · Presto taladro",
                "Te lo dejo en la portería el sábado.", "21:40", 1, false));

        conversaciones.add(new ConversacionItem(
                "3", "MD", "#faecd5", "#8c531b", "Vecinos Mz. D", "Grupo de manzana",
                "Gladys: ¿Alguien sabe a qué hora pasa...", "19:12", 0, false));

        conversaciones.add(new ConversacionItem(
                "4", "TL", "#fee4e2", "#912018", "Teresa L.", "Favor · Necesito escalera",
                "Tú: Yo tengo una, te la presto el domin...", "Ayer", 0, false));

        conversaciones.add(new ConversacionItem(
                "5", "JV", "#1f2937", "#ffffff", "Junta vecinal Los Pinos", "Canal oficial",
                "Reunión del sábado en el local comunal.", "Lun", 0, true));

        // Hilos iniciales
        List<MensajeBubble> hiloRosa = new CopyOnWriteArrayList<>();
        hiloRosa.add(new MensajeBubble("m1", "1", "Hola Rosa, buenas noches. Quería consultar por la fuga de agua en mi lavadero.", "yo", "22:50", true));
        hiloRosa.add(new MensajeBubble("m2", "1", "Hola vecino. Sí, claro, revisé tu mensaje.", "otro", "23:02", true));
        hiloRosa.add(new MensajeBubble("m3", "1", "Puedo pasar mañana a las 9, ¿le parece bien?", "otro", "23:05", false));
        hilos.put("1", hiloRosa);

        List<MensajeBubble> hiloJulio = new CopyOnWriteArrayList<>();
        hiloJulio.add(new MensajeBubble("j1", "2", "Hola Julio, ¿aún tienes disponible el taladro percutor?", "yo", "21:30", true));
        hiloJulio.add(new MensajeBubble("j2", "2", "¡Hola! Sí, está listo con todas sus brocas.", "otro", "21:35", true));
        hiloJulio.add(new MensajeBubble("j3", "2", "Te lo dejo en la portería el sábado.", "otro", "21:40", false));
        hilos.put("2", hiloJulio);
    }

    public List<ConversacionItem> listar(String filtro, String buscar) {
        return conversaciones.stream()
                .filter(c -> {
                    if (filtro == null || filtro.equalsIgnoreCase("Todos")) return true;
                    if (filtro.equalsIgnoreCase("Servicios")) return c.subtitle().contains("Servicio");
                    if (filtro.equalsIgnoreCase("Favores")) return c.subtitle().contains("Favor");
                    if (filtro.equalsIgnoreCase("Grupos")) return c.subtitle().contains("Grupo") || c.isOfficial();
                    return true;
                })
                .filter(c -> {
                    if (buscar == null || buscar.isBlank()) return true;
                    String b = buscar.toLowerCase();
                    return c.name().toLowerCase().contains(b) || c.subtitle().toLowerCase().contains(b);
                })
                .toList();
    }

    public List<MensajeBubble> obtenerMensajes(String conversacionId) {
        return hilos.computeIfAbsent(conversacionId, k -> new CopyOnWriteArrayList<>());
    }

    public MensajeBubble enviarMensaje(String conversacionId, SolicitudEnviarMensaje solicitud) {
        List<MensajeBubble> list = hilos.computeIfAbsent(conversacionId, k -> new CopyOnWriteArrayList<>());
        String hora = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        MensajeBubble nuevo = new MensajeBubble(
                UUID.randomUUID().toString(),
                conversacionId,
                solicitud.texto(),
                "yo",
                hora,
                true
        );
        list.add(nuevo);

        // Actualizar último mensaje en la conversación
        for (int i = 0; i < conversaciones.size(); i++) {
            ConversacionItem c = conversaciones.get(i);
            if (c.id().equals(conversacionId)) {
                conversaciones.set(i, new ConversacionItem(
                        c.id(), c.avatarInitials(), c.avatarBg(), c.avatarColor(),
                        c.name(), c.subtitle(), "Tú: " + solicitud.texto(), hora, 0, c.isOfficial()
                ));
                break;
            }
        }

        return nuevo;
    }

    public ConversacionItem iniciarChat(SolicitudIniciarChat solicitud) {
        // Si ya existe devolverla, sino crearla
        for (ConversacionItem c : conversaciones) {
            if (c.id().equals(solicitud.vecinoId())) return c;
        }

        ConversacionItem nueva = new ConversacionItem(
                solicitud.vecinoId(),
                "V",
                "#e0e7fb",
                "#2b4290",
                "Vecino Coordinador",
                "Coordinación vecinal",
                solicitud.mensajeInicial() != null ? solicitud.mensajeInicial() : "Chat iniciado",
                "Ahora",
                0,
                false
        );
        conversaciones.add(0, nueva);

        if (solicitud.mensajeInicial() != null && !solicitud.mensajeInicial().isBlank()) {
            enviarMensaje(nueva.id(), new SolicitudEnviarMensaje(solicitud.mensajeInicial()));
        }

        return nueva;
    }

    public boolean marcarLeido(String conversacionId) {
        for (int i = 0; i < conversaciones.size(); i++) {
            ConversacionItem c = conversaciones.get(i);
            if (c.id().equals(conversacionId)) {
                conversaciones.set(i, new ConversacionItem(
                        c.id(), c.avatarInitials(), c.avatarBg(), c.avatarColor(),
                        c.name(), c.subtitle(), c.lastMessage(), c.time(), 0, c.isOfficial()
                ));
                break;
            }
        }
        return true;
    }

    public boolean bloquearUsuario(String usuarioId) {
        return true;
    }

    public boolean reportarUsuario(String usuarioId, ReporteUsuario reporte) {
        return true;
    }
}
