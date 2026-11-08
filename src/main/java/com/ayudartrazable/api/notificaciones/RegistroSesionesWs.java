package com.ayudartrazable.api.notificaciones;

import com.ayudartrazable.api.comun.seguridad.UsuarioActual;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class RegistroSesionesWs {

    private final Map<String, UsuarioActual> sesiones = new ConcurrentHashMap<>();

    public void registrar(String sesionWs, UsuarioActual usuario) {
        sesiones.put(sesionWs, usuario);
    }

    public void quitar(String sesionWs) {
        if (sesionWs != null) {
            sesiones.remove(sesionWs);
        }
    }

    @EventListener
    public void alDesconectar(SessionDisconnectEvent evento) {
        quitar(evento.getSessionId());
    }

    public Optional<UsuarioActual> usuario(String sesionWs) {
        return sesionWs == null ? Optional.empty() : Optional.ofNullable(sesiones.get(sesionWs));
    }
}
