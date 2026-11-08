package com.ayudartrazable.api.notificaciones;

import com.ayudartrazable.api.comun.bd.LlamadorSp;
import com.ayudartrazable.api.comun.seguridad.UsuarioActual;
import com.ayudartrazable.api.comun.seguridad.ValidadorSesion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

@Component
public class InterceptorSalidaStomp implements ChannelInterceptor {

    private final RegistroSesionesWs registro;
    private final ValidadorSesion validador;
    private final LlamadorSp sp;

    public InterceptorSalidaStomp(RegistroSesionesWs registro, ValidadorSesion validador, LlamadorSp sp) {
        this.registro = registro;
        this.validador = validador;
        this.sp = sp;
    }

    @Override
    public Message<?> preSend(Message<?> mensaje, MessageChannel canal) {
        StompHeaderAccessor cabeceras = StompHeaderAccessor.wrap(mensaje);
        if (cabeceras.getMessageType() != SimpMessageType.MESSAGE) {
            return mensaje;
        }
        Optional<UsuarioActual> vigente = registro.usuario(cabeceras.getSessionId())
                .flatMap(u -> validador.validar(u.sesionId()));
        if (vigente.isEmpty()) {
            return null;
        }
        String destino = cabeceras.getDestination();
        if (destino != null && destino.startsWith(InterceptorStomp.PREFIJO_CUADRANTE)) {
            try {
                UUID cuadrante = UUID.fromString(destino.substring(InterceptorStomp.PREFIJO_CUADRANTE.length()));
                if (!sp.llamar("fn_puede_suscribir", vigente.get().usuarioId(), cuadrante).asBoolean(false)) {
                    return null;
                }
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return mensaje;
    }
}
