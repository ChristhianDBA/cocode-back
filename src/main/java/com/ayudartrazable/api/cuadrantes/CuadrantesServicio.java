package com.ayudartrazable.api.cuadrantes;

import com.ayudartrazable.api.cuadrantes.dto.CuadrantesDto.PuntoResuelto;
import com.ayudartrazable.api.identidad.IdentidadRepositorio;
import com.ayudartrazable.api.integraciones.geocodificacion.Geocodificador;
import com.ayudartrazable.api.vecinos.VecinosRepositorio;
import com.ayudartrazable.api.vecinos.dto.VecinosDto.VecinoVisible;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
public class CuadrantesServicio {

    private final CuadrantesRepositorio repositorio;
    private final IdentidadRepositorio identidad;
    private final VecinosRepositorio vecinos;
    private final Geocodificador geocodificador;

    public CuadrantesServicio(CuadrantesRepositorio repositorio, IdentidadRepositorio identidad,
            VecinosRepositorio vecinos, Geocodificador geocodificador) {
        this.repositorio = repositorio;
        this.identidad = identidad;
        this.vecinos = vecinos;
        this.geocodificador = geocodificador;
    }

    public boolean tramiteVigente(String token) {
        return token != null && identidad.tramiteVigente(token);
    }

    public PuntoResuelto resolverPin(double latitud, double longitud) {
        PuntoResuelto resuelto = repositorio.resolver(latitud, longitud);
        return resuelto.conDireccion(geocodificador.direccion(latitud, longitud).orElse(null));
    }

    public JsonNode propio(UUID usuario) {
        return repositorio.propio(usuario);
    }

    public JsonNode cercanos(UUID usuario) {
        return repositorio.cercanos(usuario);
    }

    public List<VecinoVisible> vecinos(UUID usuario) {
        return vecinos.vecinosDelCuadrante(usuario);
    }
}
