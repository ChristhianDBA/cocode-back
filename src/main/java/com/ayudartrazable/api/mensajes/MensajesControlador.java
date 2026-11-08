package com.ayudartrazable.api.mensajes;

import com.ayudartrazable.api.mensajes.dto.MensajesDto.ConversacionItem;
import com.ayudartrazable.api.mensajes.dto.MensajesDto.MensajeBubble;
import com.ayudartrazable.api.mensajes.dto.MensajesDto.ReporteUsuario;
import com.ayudartrazable.api.mensajes.dto.MensajesDto.SolicitudEnviarMensaje;
import com.ayudartrazable.api.mensajes.dto.MensajesDto.SolicitudIniciarChat;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
public class MensajesControlador {

    private final MensajesServicio servicio;

    public MensajesControlador(MensajesServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/conversaciones")
    public List<ConversacionItem> listar(
            @RequestParam(value = "filtro", required = false) String filtro,
            @RequestParam(value = "buscar", required = false) String buscar) {
        return servicio.listar(filtro, buscar);
    }

    @PostMapping("/conversaciones")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversacionItem iniciar(@Valid @RequestBody SolicitudIniciarChat solicitud) {
        return servicio.iniciarChat(solicitud);
    }

    @GetMapping("/conversaciones/{id}/mensajes")
    public List<MensajeBubble> obtenerMensajes(@PathVariable String id) {
        return servicio.obtenerMensajes(id);
    }

    @PostMapping("/conversaciones/{id}/mensajes")
    @ResponseStatus(HttpStatus.CREATED)
    public MensajeBubble enviarMensaje(
            @PathVariable String id,
            @Valid @RequestBody SolicitudEnviarMensaje solicitud) {
        return servicio.enviarMensaje(id, solicitud);
    }

    @PatchMapping("/conversaciones/{id}/leido")
    public Map<String, Object> marcarLeido(@PathVariable String id) {
        boolean ok = servicio.marcarLeido(id);
        return Map.of("exitoso", ok, "conversacionId", id);
    }

    @PostMapping("/usuarios/{id}/bloquear")
    public Map<String, Object> bloquear(@PathVariable String id) {
        boolean ok = servicio.bloquearUsuario(id);
        return Map.of("exitoso", ok, "mensaje", "Usuario bloqueado");
    }

    @PostMapping("/usuarios/{id}/reportar")
    public Map<String, Object> reportar(
            @PathVariable String id,
            @Valid @RequestBody ReporteUsuario reporte) {
        boolean ok = servicio.reportarUsuario(id, reporte);
        return Map.of("exitoso", ok, "mensaje", "Reporte recibido");
    }
}
