package com.ayudartrazable.api.emergencias;

import com.ayudartrazable.api.emergencias.dto.EmergenciasDto.EstadoAlerta;
import com.ayudartrazable.api.emergencias.dto.EmergenciasDto.RespuestaPanico;
import com.ayudartrazable.api.emergencias.dto.EmergenciasDto.ResultadoAlerta;
import com.ayudartrazable.api.emergencias.dto.EmergenciasDto.TipoReporte;
import com.ayudartrazable.api.notificaciones.DifusionAlertas;
import com.ayudartrazable.api.notificaciones.dto.EventoAlerta;
import com.ayudartrazable.api.vecinos.VecinosRepositorio;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
public class EmergenciasServicio {

    private final EmergenciasRepositorio repositorio;
    private final DifusionAlertas difusion;
    private final VecinosRepositorio vecinos;

    public EmergenciasServicio(EmergenciasRepositorio repositorio, DifusionAlertas difusion,
            VecinosRepositorio vecinos) {
        this.repositorio = repositorio;
        this.difusion = difusion;
        this.vecinos = vecinos;
    }

    public RespuestaPanico panico(UUID usuario, Boolean confirmado, Double latitud, Double longitud) {
        ResultadoAlerta r = repositorio.emitirPanico(usuario, confirmado, latitud, longitud);
        if (r.fueCreada()) {
            difusion.difundir(EventoAlerta.ALERTA_CREADA, r.alerta(), r.alertaConUbicacion(), r.destinatarios(), true);
        }
        return new RespuestaPanico(r.fueCreada(), r.estaFueraDeCobertura(), r.alerta(),
                r.estaFueraDeCobertura() ? vecinos.telefonos(usuario) : null);
    }

    public JsonNode reporte(UUID usuario, TipoReporte tipo, String descripcion) {
        ResultadoAlerta r = repositorio.emitirReporte(usuario, tipo.name(), descripcion);
        difusion.difundir(EventoAlerta.ALERTA_CREADA, r.alerta(), r.alertaConUbicacion(), r.destinatarios(), true);
        return r.alerta();
    }

    public JsonNode activas(UUID usuario) {
        return repositorio.activas(usuario);
    }

    public JsonNode cambiarEstado(UUID usuario, boolean esSerenazgo, UUID alerta, EstadoAlerta estado) {
        ResultadoAlerta r = repositorio.cambiarEstado(usuario, alerta, estado.name());
        difusion.difundir(EventoAlerta.ALERTA_ACTUALIZADA, r.alerta(), r.alertaConUbicacion(), r.destinatarios(), false);
        return esSerenazgo ? r.alertaConUbicacion() : r.alerta();
    }
}
