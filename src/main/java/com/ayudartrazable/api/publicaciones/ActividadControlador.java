package com.ayudartrazable.api.publicaciones;

import com.ayudartrazable.api.publicaciones.dto.PublicacionesDto.ItemActividad;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/actividad")
public class ActividadControlador {

    private final PublicacionesServicio servicio;

    public ActividadControlador(PublicacionesServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/mis-publicaciones")
    public List<ItemActividad> misPublicaciones() {
        return servicio.listarMisPublicaciones();
    }

    @GetMapping("/hice")
    public List<ItemActividad> favoresHice() {
        return servicio.listarFavoresHice();
    }

    @GetMapping("/pedi")
    public List<ItemActividad> favoresPedi() {
        return servicio.listarFavoresPedi();
    }

    @PostMapping("/favores/{id}/confirmar-devolucion")
    public Map<String, Object> confirmarDevolucion(@PathVariable String id) {
        boolean ok = servicio.confirmarDevolucion(id);
        return Map.of("exitoso", ok, "mensaje", "Devolución confirmada con éxito.");
    }
}
