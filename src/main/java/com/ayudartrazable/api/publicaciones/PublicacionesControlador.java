package com.ayudartrazable.api.publicaciones;

import com.ayudartrazable.api.comun.seguridad.UsuarioActual;
import com.ayudartrazable.api.publicaciones.dto.PublicacionesDto.CambioEstado;
import com.ayudartrazable.api.publicaciones.dto.PublicacionesDto.PublicacionItem;
import com.ayudartrazable.api.publicaciones.dto.PublicacionesDto.RespuestaSolicitudFavor;
import com.ayudartrazable.api.publicaciones.dto.PublicacionesDto.SolicitudFavor;
import com.ayudartrazable.api.publicaciones.dto.PublicacionesDto.SolicitudPublicacion;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/publicaciones")
public class PublicacionesControlador {

    private final PublicacionesServicio servicio;

    public PublicacionesControlador(PublicacionesServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<PublicacionItem> listar(
            @RequestParam(value = "categoria", required = false) String categoria,
            @RequestParam(value = "tipo", required = false) String tipo,
            @RequestParam(value = "buscar", required = false) String buscar) {
        return servicio.listar(categoria, tipo, buscar);
    }

    @GetMapping("/{id}")
    public PublicacionItem obtener(@PathVariable String id) {
        return servicio.obtenerPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PublicacionItem crear(
            @AuthenticationPrincipal UsuarioActual usuario,
            @Valid @RequestBody SolicitudPublicacion solicitud) {
        String nombreAutor = usuario != null ? "Vecino Verificado" : "Tú";
        return servicio.crear(solicitud, nombreAutor);
    }

    @PostMapping("/{id}/solicitar")
    public RespuestaSolicitudFavor solicitarFavor(
            @PathVariable String id,
            @RequestBody(required = false) SolicitudFavor solicitud,
            @AuthenticationPrincipal UsuarioActual usuario) {
        String solicitante = usuario != null ? usuario.usuarioId().toString() : "Vecino";
        return servicio.solicitarFavor(id, solicitud, solicitante);
    }

    @PatchMapping("/{id}/estado")
    public Map<String, Object> cambiarEstado(
            @PathVariable String id,
            @Valid @RequestBody CambioEstado cambio) {
        boolean ok = servicio.cambiarEstado(id, cambio);
        return Map.of("exitoso", ok, "id", id, "nuevoEstado", cambio.estado());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> eliminar(@PathVariable String id) {
        boolean ok = servicio.eliminar(id);
        return Map.of("eliminado", ok, "id", id);
    }
}
