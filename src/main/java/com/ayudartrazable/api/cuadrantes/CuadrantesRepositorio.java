package com.ayudartrazable.api.cuadrantes;

import com.ayudartrazable.api.comun.bd.LlamadorSp;
import com.ayudartrazable.api.cuadrantes.dto.CuadrantesDto.PuntoResuelto;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;

@Repository
public class CuadrantesRepositorio {

    private final LlamadorSp sp;

    public CuadrantesRepositorio(LlamadorSp sp) {
        this.sp = sp;
    }

    public PuntoResuelto resolver(double latitud, double longitud) {
        return sp.llamar(PuntoResuelto.class, "fn_resolver_punto", latitud, longitud);
    }

    public JsonNode propio(UUID usuario) {
        return sp.llamar("fn_cuadrante_propio", usuario);
    }

    public JsonNode cercanos(UUID usuario) {
        return sp.llamar("fn_cuadrantes_cercanos", usuario);
    }
}
