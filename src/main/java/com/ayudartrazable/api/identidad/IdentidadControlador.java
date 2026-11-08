package com.ayudartrazable.api.identidad;

import com.ayudartrazable.api.comun.seguridad.UsuarioActual;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.EstadoTramite;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.RespuestaCodigo;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.RespuestaDomicilio;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.RespuestaIngreso;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.RespuestaIngresoCelular;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.RespuestaSesion;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.RespuestaTramite;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.RespuestaVerificacion;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.SolicitudAlta;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.SolicitudCodigo;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.SolicitudDni;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.SolicitudDomicilio;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.SolicitudIngreso;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.SolicitudIngresoCelular;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.SolicitudRenovar;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.SolicitudVerificacion;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.SolicitudVerificarCelular;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.SolicitudVerificarIngreso;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class IdentidadControlador {

    public static final String CABECERA_TRAMITE = "X-Tramite";

    private final IdentidadServicio servicio;

    public IdentidadControlador(IdentidadServicio servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/dni")
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaTramite validarDni(@Valid @RequestBody SolicitudDni solicitud) {
        return servicio.iniciarTramite("ALTA", solicitud);
    }

    @GetMapping("/reniec/{dni}")
    public com.ayudartrazable.api.integraciones.registrocivil.DatosCiviles consultarReniec(@org.springframework.web.bind.annotation.PathVariable String dni) {
        return servicio.consultarReniec(dni);
    }

    @GetMapping("/tramite")
    public EstadoTramite estado(@RequestHeader(CABECERA_TRAMITE) String token) {
        return servicio.estadoTramite(token);
    }

    @PostMapping("/codigo")
    public RespuestaCodigo solicitarCodigo(
            @RequestHeader(value = CABECERA_TRAMITE, required = false) String token,
            @Valid @RequestBody SolicitudCodigo solicitud) {
        return servicio.solicitarCodigo(token, solicitud.celular(), solicitud.canal());
    }

    @PostMapping("/codigo/verificar")
    public RespuestaVerificacion verificarCodigo(
            @RequestHeader(value = CABECERA_TRAMITE, required = false) String token,
            @Valid @RequestBody SolicitudVerificacion solicitud) {
        return servicio.verificarCodigo(token, solicitud.codigo(), solicitud.celular());
    }

    @PostMapping("/alta/domicilio")
    public RespuestaDomicilio guardarDomicilio(@RequestHeader(CABECERA_TRAMITE) String token,
            @Valid @RequestBody SolicitudDomicilio solicitud) {
        return servicio.guardarDomicilio(token, solicitud.latitud(), solicitud.longitud());
    }

    @PostMapping("/alta")
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaSesion completarAlta(@RequestHeader(CABECERA_TRAMITE) String token,
            @RequestBody SolicitudAlta solicitud) {
        return servicio.completarAlta(token, solicitud.aceptaTratamiento());
    }

    @PostMapping("/ingreso")
    public RespuestaIngreso ingreso(@Valid @RequestBody SolicitudIngreso solicitud) {
        return servicio.solicitarIngreso(solicitud.dni(), solicitud.canal());
    }

    @PostMapping("/ingreso/celular")
    public RespuestaIngresoCelular ingresoPorCelular(@Valid @RequestBody SolicitudIngresoCelular solicitud) {
        return servicio.solicitarIngresoPorCelular(solicitud.celular(), solicitud.canal());
    }

    @PostMapping("/ingreso/celular/verificar")
    public RespuestaSesion verificarIngresoPorCelular(@Valid @RequestBody SolicitudVerificarCelular solicitud) {
        return servicio.verificarIngresoPorCelular(solicitud.tokenIngreso(), solicitud.celular(), solicitud.codigo());
    }

    @PostMapping("/ingreso/verificar")
    public RespuestaSesion verificarIngreso(@Valid @RequestBody SolicitudVerificarIngreso solicitud) {
        return servicio.verificarIngreso(solicitud.tokenIngreso(), solicitud.codigo());
    }

    @PostMapping("/renovar")
    public RespuestaSesion renovar(@Valid @RequestBody SolicitudRenovar solicitud) {
        return servicio.renovar(solicitud.tokenRenovacion());
    }

    @PostMapping("/recuperacion/dni")
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaTramite iniciarRecuperacion(@Valid @RequestBody SolicitudDni solicitud) {
        return servicio.iniciarTramite("RECUPERACION", solicitud);
    }

    @PostMapping("/recuperacion/completar")
    public ResponseEntity<Object> completarRecuperacion(@RequestHeader(CABECERA_TRAMITE) String token) {
        Object resultado = servicio.completarRecuperacion(token);
        return ResponseEntity.status(resultado instanceof RespuestaSesion ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .body(resultado);
    }

    @PostMapping("/salir")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void salir(@AuthenticationPrincipal UsuarioActual usuario) {
        servicio.cerrar(usuario.sesionId());
    }
}
