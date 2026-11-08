package com.ayudartrazable.api.vecinos;

import com.ayudartrazable.api.comun.seguridad.UsuarioActual;
import com.ayudartrazable.api.vecinos.dto.ResenasDto.PerfilVecinoPublico;
import com.ayudartrazable.api.vecinos.dto.ResenasDto.ResenaItem;
import com.ayudartrazable.api.vecinos.dto.ResenasDto.RespuestaVistoBueno;
import com.ayudartrazable.api.vecinos.dto.ResenasDto.SolicitudResena;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vecinos")
public class PerfilPublicoControlador {

    private final ResenasServicio servicio;

    public PerfilPublicoControlador(ResenasServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/{id}/perfil")
    public PerfilVecinoPublico obtenerPerfil(@PathVariable String id) {
        return servicio.obtenerPerfil(id);
    }

    @PostMapping("/{id}/visto-bueno")
    public RespuestaVistoBueno alternarVistoBueno(@PathVariable String id) {
        return servicio.alternarVistoBueno(id);
    }

    @GetMapping("/{id}/resenas")
    public List<ResenaItem> listarResenas(@PathVariable String id) {
        return servicio.listarResenas(id);
    }

    @PostMapping("/{id}/resenas")
    @ResponseStatus(HttpStatus.CREATED)
    public ResenaItem agregarResena(
            @PathVariable String id,
            @Valid @RequestBody SolicitudResena solicitud,
            @AuthenticationPrincipal UsuarioActual usuario) {
        String autor = usuario != null ? "Vecino Verificado" : "Carlos M.";
        return servicio.agregarResena(id, solicitud, autor);
    }
}
