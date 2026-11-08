package com.ayudartrazable.api.emergencias;

import com.ayudartrazable.api.comun.seguridad.UsuarioActual;
import com.ayudartrazable.api.emergencias.dto.TableroEmergenciaDto.AporteItem;
import com.ayudartrazable.api.emergencias.dto.TableroEmergenciaDto.DirectorioLocalResponse;
import com.ayudartrazable.api.emergencias.dto.TableroEmergenciaDto.EstadoTableroResponse;
import com.ayudartrazable.api.emergencias.dto.TableroEmergenciaDto.NecesidadItem;
import com.ayudartrazable.api.emergencias.dto.TableroEmergenciaDto.RespuestaConfirmacion;
import com.ayudartrazable.api.emergencias.dto.TableroEmergenciaDto.SolicitudActivarTablero;
import com.ayudartrazable.api.emergencias.dto.TableroEmergenciaDto.SolicitudAporte;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/emergencias")
public class TableroEmergenciaControlador {

    private final TableroEmergenciaServicio servicio;

    public TableroEmergenciaControlador(TableroEmergenciaServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/directorio-local")
    public DirectorioLocalResponse directorioLocal(@RequestParam(value = "cuadrante", required = false) String cuadrante) {
        return servicio.obtenerDirectorioLocal(cuadrante);
    }

    @GetMapping("/tablero/activo")
    public EstadoTableroResponse estadoTablero(@RequestParam(value = "cuadrante", required = false) String cuadrante) {
        return servicio.obtenerEstadoTablero(cuadrante);
    }

    @PostMapping("/tablero/solicitar")
    @ResponseStatus(HttpStatus.CREATED)
    public EstadoTableroResponse solicitarActivacion(@Valid @RequestBody SolicitudActivarTablero solicitud) {
        return servicio.solicitarActivacion(solicitud);
    }

    @PostMapping("/tablero/{id}/confirmar")
    public RespuestaConfirmacion confirmar(@PathVariable String id) {
        return servicio.confirmarSolicitud(id);
    }

    @PostMapping("/tablero/{id}/estoy-a-salvo")
    public Map<String, Object> estoyASalvo(@PathVariable String id) {
        int total = servicio.registrarEstoyASalvo(id);
        return Map.of("exitoso", true, "personasASalvo", total);
    }

    @GetMapping("/tablero/{id}/necesidades")
    public List<NecesidadItem> necesidades(@PathVariable String id) {
        return servicio.listarNecesidades(id);
    }

    @PostMapping("/tablero/{id}/aportes")
    @ResponseStatus(HttpStatus.CREATED)
    public AporteItem registrarAporte(
            @PathVariable String id,
            @Valid @RequestBody SolicitudAporte solicitud,
            @AuthenticationPrincipal UsuarioActual usuario) {
        String vecino = usuario != null ? "Vecino Verificado" : "Tú";
        return servicio.registrarAporte(id, solicitud, vecino);
    }
}
