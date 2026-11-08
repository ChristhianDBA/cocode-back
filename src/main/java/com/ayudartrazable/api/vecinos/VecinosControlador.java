package com.ayudartrazable.api.vecinos;

import com.ayudartrazable.api.comun.seguridad.UsuarioActual;
import com.ayudartrazable.api.identidad.IdentidadServicio;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.RespuestaDomicilio;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.SolicitudDomicilio;
import com.ayudartrazable.api.vecinos.dto.ResenasDto.SolicitudOcupacion;
import com.ayudartrazable.api.vecinos.dto.VecinosDto.DispositivoRegistrado;
import com.ayudartrazable.api.vecinos.dto.VecinosDto.Perfil;
import com.ayudartrazable.api.vecinos.dto.VecinosDto.ReporteRecibido;
import com.ayudartrazable.api.vecinos.dto.VecinosDto.SolicitudDispositivo;
import com.ayudartrazable.api.vecinos.dto.VecinosDto.SolicitudReporteDatos;
import com.ayudartrazable.api.vecinos.dto.VecinosDto.TelefonosEmergencia;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/vecinos")
public class VecinosControlador {

    private final VecinosServicio servicio;
    private final IdentidadServicio identidad;
    private final ResenasServicio resenas;

    public VecinosControlador(VecinosServicio servicio, IdentidadServicio identidad, ResenasServicio resenas) {
        this.identidad = identidad;
        this.servicio = servicio;
        this.resenas = resenas;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public Perfil perfil(@AuthenticationPrincipal UsuarioActual usuario) {
        return servicio.perfil(usuario.usuarioId());
    }

    @PutMapping("/me/domicilio")
    @PreAuthorize("hasRole('VECINO')")
    public RespuestaDomicilio cambiarDomicilio(@AuthenticationPrincipal UsuarioActual usuario,
            @Valid @RequestBody SolicitudDomicilio solicitud) {
        return servicio.cambiarDomicilio(usuario.usuarioId(), solicitud.latitud(), solicitud.longitud());
    }

    @PutMapping("/me/dispositivo")
    @PreAuthorize("isAuthenticated()")
    public DispositivoRegistrado registrarDispositivo(@AuthenticationPrincipal UsuarioActual usuario,
            @Valid @RequestBody SolicitudDispositivo solicitud) {
        return servicio.registrarDispositivo(usuario.usuarioId(), usuario.sesionId(), solicitud.tokenPush());
    }

    @GetMapping("/telefonos-emergencia")
    @PreAuthorize("isAuthenticated()")
    public TelefonosEmergencia telefonos(@AuthenticationPrincipal UsuarioActual usuario) {
        return servicio.telefonos(usuario.usuarioId());
    }

    @PostMapping("/me/recuperacion/cancelar")
    @PreAuthorize("hasRole('VECINO')")
    public JsonNode cancelarRecuperacion(@AuthenticationPrincipal UsuarioActual usuario) {
        return identidad.cancelarRecuperacion(usuario.usuarioId());
    }

    @PostMapping("/me/reporte-datos")
    @PreAuthorize("hasRole('VECINO')")
    @ResponseStatus(HttpStatus.CREATED)
    public ReporteRecibido reportarDatos(@AuthenticationPrincipal UsuarioActual usuario,
            @Valid @RequestBody SolicitudReporteDatos solicitud) {
        return servicio.reportarDatos(usuario.usuarioId(), solicitud.descripcion());
    }

    @GetMapping("/me/ocupacion")
    public Map<String, Object> obtenerOcupacion(@AuthenticationPrincipal UsuarioActual usuario) {
        return resenas.obtenerOcupacion(usuario != null ? usuario.usuarioId() : null);
    }

    @PutMapping("/me/ocupacion")
    public Map<String, Object> actualizarOcupacion(
            @AuthenticationPrincipal UsuarioActual usuario,
            @RequestBody SolicitudOcupacion solicitud) {
        boolean ok = resenas.actualizarOcupacion(usuario != null ? usuario.usuarioId() : null, solicitud);
        return Map.of("exitoso", ok, "mensaje", "Ocupación actualizada correctamente en base de datos");
    }
}
