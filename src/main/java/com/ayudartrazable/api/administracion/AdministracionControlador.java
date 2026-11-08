package com.ayudartrazable.api.administracion;

import com.ayudartrazable.api.administracion.dto.AdministracionDto.SolicitudCoordinador;
import com.ayudartrazable.api.administracion.dto.AdministracionDto.SolicitudRetiro;
import com.ayudartrazable.api.administracion.dto.AdministracionDto.SolicitudSerenazgo;
import com.ayudartrazable.api.comun.seguridad.UsuarioActual;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@Validated
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdministracionControlador {

    private final AdministracionServicio servicio;

    public AdministracionControlador(AdministracionServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/usuarios")
    public JsonNode buscarUsuario(@AuthenticationPrincipal UsuarioActual admin,
            @RequestParam @Pattern(regexp = "\\d{8}", message = "El DNI debe tener 8 dígitos") String dni) {
        return servicio.buscarUsuario(admin.usuarioId(), dni);
    }

    @PostMapping("/coordinadores")
    @ResponseStatus(HttpStatus.CREATED)
    public JsonNode asignarCoordinador(@AuthenticationPrincipal UsuarioActual admin,
            @Valid @RequestBody SolicitudCoordinador solicitud) {
        return servicio.asignarCoordinador(admin.usuarioId(), solicitud.usuarioId(), solicitud.cuadrantes());
    }

    @PostMapping("/coordinadores/{usuarioId}/retirar")
    public JsonNode retirarCoordinador(@AuthenticationPrincipal UsuarioActual admin, @PathVariable UUID usuarioId,
            @RequestBody(required = false) SolicitudRetiro solicitud) {
        return servicio.retirarCoordinador(admin.usuarioId(), usuarioId, solicitud == null ? null : solicitud.cuadranteId());
    }

    @PostMapping("/serenazgo")
    @ResponseStatus(HttpStatus.CREATED)
    public JsonNode altaSerenazgo(@AuthenticationPrincipal UsuarioActual admin,
            @Valid @RequestBody SolicitudSerenazgo solicitud) {
        return servicio.altaSerenazgo(admin.usuarioId(), solicitud.dni(), solicitud.celular(), solicitud.distritoId(),
                solicitud.fechaNacimiento(), solicitud.sexo());
    }

    @PostMapping("/serenazgo/{usuarioId}/desactivar")
    public JsonNode desactivarSerenazgo(@AuthenticationPrincipal UsuarioActual admin, @PathVariable UUID usuarioId) {
        return servicio.desactivarSerenazgo(admin.usuarioId(), usuarioId);
    }

    @GetMapping("/avisos")
    public JsonNode avisos(@AuthenticationPrincipal UsuarioActual admin) {
        return servicio.avisos(admin.usuarioId());
    }

    @PostMapping("/suspensiones/{suspensionId}/levantar")
    public JsonNode levantarSuspension(@AuthenticationPrincipal UsuarioActual admin, @PathVariable UUID suspensionId) {
        return servicio.levantarSuspension(admin.usuarioId(), suspensionId);
    }

    @PostMapping("/cuadrantes/{cuadranteId}/deshabilitar")
    public JsonNode deshabilitar(@AuthenticationPrincipal UsuarioActual admin, @PathVariable UUID cuadranteId) {
        return servicio.deshabilitarCuadrante(admin.usuarioId(), cuadranteId);
    }

    @PostMapping("/cuadrantes/{cuadranteId}/habilitar")
    public JsonNode habilitar(@AuthenticationPrincipal UsuarioActual admin, @PathVariable UUID cuadranteId) {
        return servicio.habilitarCuadrante(admin.usuarioId(), cuadranteId);
    }
}
