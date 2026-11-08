package com.ayudartrazable.api.notificaciones;

import com.ayudartrazable.api.comun.bd.LlamadorSp;
import com.ayudartrazable.api.comun.seguridad.AutenticacionUsuario;
import com.ayudartrazable.api.comun.seguridad.UsuarioActual;
import com.ayudartrazable.api.comun.seguridad.ValidadorSesion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

@Component
public class InterceptorStomp implements ChannelInterceptor {

    static final String PREFIJO_CUADRANTE = "/topic/cuadrante/";

    private final JwtDecoder decodificador;
    private final ValidadorSesion validador;
    private final LlamadorSp sp;

    private final RegistroSesionesWs registro;

    public InterceptorStomp(JwtDecoder decodificador, ValidadorSesion validador, LlamadorSp sp,
            RegistroSesionesWs registro) {
        this.registro = registro;
        this.decodificador = decodificador;
        this.validador = validador;
        this.sp = sp;
    }

    @Override
    public Message<?> preSend(Message<?> mensaje, MessageChannel canal) {
        StompHeaderAccessor cabeceras = MessageHeaderAccessor.getAccessor(mensaje, StompHeaderAccessor.class);
        if (cabeceras == null || cabeceras.getCommand() == null) {
            return mensaje;
        }
        StompCommand comando = cabeceras.getCommand();
        if (comando == StompCommand.CONNECT) {
            UsuarioActual usuario = autenticar(cabeceras.getFirstNativeHeader("Authorization"));
            cabeceras.setUser(new AutenticacionUsuario(usuario));
            registro.registrar(cabeceras.getSessionId(), usuario);
        } else if (comando == StompCommand.DISCONNECT) {
            registro.quitar(cabeceras.getSessionId());
        } else if (comando == StompCommand.SUBSCRIBE) {
            autorizarSuscripcion(usuario(cabeceras), cabeceras.getDestination());
        } else if (comando == StompCommand.SEND) {
            throw new MessagingException("Operación no permitida");
        }
        return mensaje;
    }

    private UsuarioActual autenticar(String cabecera) {
        if (cabecera == null || !cabecera.startsWith("Bearer ")) {
            throw new MessagingException("Tu sesión no es válida. Inicia sesión nuevamente");
        }
        try {
            UUID sesion = UUID.fromString(decodificador.decode(cabecera.substring(7)).getSubject());
            return validador.validar(sesion)
                    .orElseThrow(() -> new MessagingException("Tu sesión no es válida. Inicia sesión nuevamente"));
        } catch (JwtException | IllegalArgumentException e) {
            throw new MessagingException("Tu sesión no es válida. Inicia sesión nuevamente");
        }
    }

    private UsuarioActual usuario(StompHeaderAccessor cabeceras) {
        if (cabeceras.getUser() instanceof AutenticacionUsuario autenticacion) {
            UsuarioActual actual = autenticacion.getPrincipal();
            return validador.validar(actual.sesionId())
                    .orElseThrow(() -> new MessagingException("Tu sesión no es válida. Inicia sesión nuevamente"));
        }
        throw new MessagingException("Tu sesión no es válida. Inicia sesión nuevamente");
    }

    private void autorizarSuscripcion(UsuarioActual usuario, String destino) {
        if (destino == null) {
            throw new MessagingException("No tienes permiso para realizar esta acción");
        }
        if (destino.equals("/user" + DifusionAlertas.COLA_USUARIO)) {
            return;
        }
        if (destino.startsWith(PREFIJO_CUADRANTE)) {
            Optional<UUID> cuadrante = uuid(destino.substring(PREFIJO_CUADRANTE.length()));
            if (cuadrante.isPresent() && puedeSuscribir(usuario.usuarioId(), cuadrante.get())) {
                return;
            }
        }
        throw new MessagingException("No tienes permiso para realizar esta acción");
    }

    private boolean puedeSuscribir(UUID usuario, UUID cuadrante) {
        return sp.llamar("fn_puede_suscribir", usuario, cuadrante).asBoolean(false);
    }

    private static Optional<UUID> uuid(String texto) {
        try {
            return Optional.of(UUID.fromString(texto));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
