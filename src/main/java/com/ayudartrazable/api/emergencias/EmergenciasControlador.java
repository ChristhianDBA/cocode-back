package com.ayudartrazable.api.emergencias;

import com.ayudartrazable.api.comun.seguridad.UsuarioActual;
import com.ayudartrazable.api.emergencias.dto.EmergenciasDto.RespuestaPanico;
import com.ayudartrazable.api.emergencias.dto.EmergenciasDto.SolicitudEstado;
import com.ayudartrazable.api.emergencias.dto.EmergenciasDto.SolicitudPanico;
import com.ayudartrazable.api.emergencias.dto.EmergenciasDto.SolicitudReporte;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/emergencias")
public class EmergenciasControlador {

    private final EmergenciasServicio servicio;

    public EmergenciasControlador(EmergenciasServicio servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/panico")
    @PreAuthorize("hasRole('VECINO')")
    public ResponseEntity<RespuestaPanico> panico(@AuthenticationPrincipal UsuarioActual usuario,
            @Valid @RequestBody SolicitudPanico solicitud) {
        RespuestaPanico r = servicio.panico(usuario.usuarioId(), solicitud.confirmado(), solicitud.latitud(),
                solicitud.longitud());
        return ResponseEntity.status(r.creada() ? HttpStatus.CREATED : HttpStatus.OK).body(r);
    }

    @PostMapping("/reportes")
    @PreAuthorize("hasRole('VECINO')")
    @ResponseStatus(HttpStatus.CREATED)
    public JsonNode reporte(@AuthenticationPrincipal UsuarioActual usuario,
            @Valid @RequestBody SolicitudReporte solicitud) {
        return servicio.reporte(usuario.usuarioId(), solicitud.tipo(), solicitud.descripcion());
    }

    @GetMapping("/activas")
    @PreAuthorize("hasAnyRole('VECINO', 'SERENAZGO')")
    public JsonNode activas(@AuthenticationPrincipal UsuarioActual usuario) {
        return servicio.activas(usuario.usuarioId());
    }

    @PatchMapping("/{alertaId}/estado")
    @PreAuthorize("hasAnyRole('COORDINADOR', 'SERENAZGO')")
    public JsonNode cambiarEstado(@AuthenticationPrincipal UsuarioActual usuario, @PathVariable UUID alertaId,
            @Valid @RequestBody SolicitudEstado solicitud) {
        return servicio.cambiarEstado(usuario.usuarioId(), usuario.tieneRol("SERENAZGO"), alertaId, solicitud.estado());
    }
}
