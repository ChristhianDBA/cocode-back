package com.ayudartrazable.api.cuadrantes;

import com.ayudartrazable.api.comun.seguridad.UsuarioActual;
import com.ayudartrazable.api.cuadrantes.dto.CuadrantesDto.PinMapa;
import com.ayudartrazable.api.cuadrantes.dto.CuadrantesDto.PuntoResuelto;
import com.ayudartrazable.api.publicaciones.PublicacionesServicio;
import com.ayudartrazable.api.vecinos.dto.VecinosDto.VecinoVisible;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@Validated
@RequestMapping("/api/v1/geo")
public class CuadrantesControlador {

    private final CuadrantesServicio servicio;
    private final PublicacionesServicio publicaciones;

    public CuadrantesControlador(CuadrantesServicio servicio, PublicacionesServicio publicaciones) {
        this.servicio = servicio;
        this.publicaciones = publicaciones;
    }

    @GetMapping("/resolver-pin")
    public PuntoResuelto resolverPin(
            @RequestParam("lat") @DecimalMin("-90") @DecimalMax("90") double latitud,
            @RequestParam("lng") @DecimalMin("-180") @DecimalMax("180") double longitud) {
        return servicio.resolverPin(latitud, longitud);
    }

    @GetMapping("/cuadrantes/mio")
    @PreAuthorize("hasRole('VECINO')")
    public JsonNode propio(@AuthenticationPrincipal UsuarioActual usuario) {
        return servicio.propio(usuario.usuarioId());
    }

    @GetMapping("/cuadrantes/cercanos")
    @PreAuthorize("hasRole('VECINO')")
    public JsonNode cercanos(@AuthenticationPrincipal UsuarioActual usuario) {
        return servicio.cercanos(usuario.usuarioId());
    }

    @GetMapping("/cuadrantes/mio/vecinos")
    @PreAuthorize("hasRole('VECINO')")
    public List<VecinoVisible> vecinos(@AuthenticationPrincipal UsuarioActual usuario) {
        return servicio.vecinos(usuario.usuarioId());
    }

    @GetMapping("/mapa/pines")
    public List<PinMapa> pinesMapa(
            @RequestParam(value = "categoria", required = false) String categoria,
            @RequestParam(value = "tag", required = false) String tag) {
        return publicaciones.listarPinesMapa(categoria, tag);
    }
}
