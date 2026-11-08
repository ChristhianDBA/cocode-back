package com.ayudartrazable.api.cuadrantes;

import com.ayudartrazable.api.comun.errores.ExcepcionNegocio;
import com.ayudartrazable.api.comun.seguridad.UsuarioActual;
import com.ayudartrazable.api.cuadrantes.dto.CuadrantesDto.PuntoResuelto;
import com.ayudartrazable.api.identidad.IdentidadControlador;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import com.ayudartrazable.api.vecinos.dto.VecinosDto.VecinoVisible;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import tools.jackson.databind.JsonNode;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/geo")
public class CuadrantesControlador {

    private final CuadrantesServicio servicio;

    public CuadrantesControlador(CuadrantesServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/resolver-pin")
    public PuntoResuelto resolverPin(
            @RequestParam("lat") @DecimalMin("-90") @DecimalMax("90") double latitud,
            @RequestParam("lng") @DecimalMin("-180") @DecimalMax("180") double longitud,
            @RequestHeader(value = IdentidadControlador.CABECERA_TRAMITE, required = false) String tramite,
            @AuthenticationPrincipal UsuarioActual usuario) {
        if (usuario == null && !servicio.tramiteVigente(tramite)) {
            throw new ExcepcionNegocio(401, "NO_AUTENTICADO", "Tu sesión no es válida. Inicia sesión nuevamente");
        }
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
    public List<com.ayudartrazable.api.cuadrantes.dto.CuadrantesDto.PinMapa> pinesMapa(
            @RequestParam(value = "categoria", required = false) String categoria,
            @RequestParam(value = "tag", required = false) String tag) {
        List<com.ayudartrazable.api.cuadrantes.dto.CuadrantesDto.PinMapa> list = List.of(
            new com.ayudartrazable.api.cuadrantes.dto.CuadrantesDto.PinMapa("1", "RQ", "Rosa Quispe", "Gasfitería", 42, "180 m", "2 min a pie", -12.0892, -76.9890, "servicios", "Gasfitería"),
            new com.ayudartrazable.api.cuadrantes.dto.CuadrantesDto.PinMapa("2", "MH", "Miguel Huamán", "Electricista domiciliario", 31, "350 m", "4 min a pie", -12.0920, -76.9922, "servicios", "Electricidad"),
            new com.ayudartrazable.api.cuadrantes.dto.CuadrantesDto.PinMapa("3", "LT", "Lucía Torres", "Clases de matemática", 27, "420 m", "5 min a pie", -12.0918, -76.9875, "servicios", "Clases"),
            new com.ayudartrazable.api.cuadrantes.dto.CuadrantesDto.PinMapa("4", "JC", "Jorge Córdova", "Cerrajería y carpintería", 19, "600 m", "7 min a pie", -12.0875, -76.9935, "servicios", "Todos"),
            new com.ayudartrazable.api.cuadrantes.dto.CuadrantesDto.PinMapa("5", "JR", "Julio R. · Mz. A", "Presto taladro percutor", 23, "150 m", "2 min a pie", -12.0895, -76.9915, "favores", "Todos"),
            new com.ayudartrazable.api.cuadrantes.dto.CuadrantesDto.PinMapa("6", "TL", "Teresa L.", "Presto escalera telescópica", 15, "310 m", "4 min a pie", -12.0910, -76.9880, "favores", "Todos")
        );

        return list.stream()
                .filter(p -> categoria == null || categoria.isBlank() || p.category().equalsIgnoreCase(categoria))
                .filter(p -> tag == null || tag.isBlank() || tag.equalsIgnoreCase("Todos") || p.tag().equalsIgnoreCase(tag))
                .toList();
    }
}