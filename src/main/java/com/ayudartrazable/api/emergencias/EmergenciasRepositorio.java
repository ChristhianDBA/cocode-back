package com.ayudartrazable.api.emergencias;

import com.ayudartrazable.api.comun.bd.LlamadorSp;
import com.ayudartrazable.api.emergencias.dto.EmergenciasDto.ResultadoAlerta;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;

@Repository
public class EmergenciasRepositorio {

    private final LlamadorSp sp;

    public EmergenciasRepositorio(LlamadorSp sp) {
        this.sp = sp;
    }

    public ResultadoAlerta emitirPanico(UUID usuario, Boolean confirmado, Double latitud, Double longitud) {
        return sp.llamar(ResultadoAlerta.class, "sp_panico_emitir", usuario, confirmado, latitud, longitud);
    }

    public ResultadoAlerta emitirReporte(UUID usuario, String tipo, String descripcion) {
        return sp.llamar(ResultadoAlerta.class, "sp_reporte_emitir", usuario, tipo, descripcion);
    }

    public JsonNode activas(UUID usuario) {
        return sp.llamar("fn_alertas_activas", usuario);
    }

    public ResultadoAlerta cambiarEstado(UUID usuario, UUID alerta, String estado) {
        return sp.llamar(ResultadoAlerta.class, "sp_alerta_cambiar_estado", usuario, alerta, estado);
    }
}
