package com.ayudartrazable.api.vecinos;

import com.ayudartrazable.api.identidad.dto.IdentidadDto.RespuestaDomicilio;
import com.ayudartrazable.api.integraciones.geocodificacion.Geocodificador;
import com.ayudartrazable.api.vecinos.dto.VecinosDto.DispositivoRegistrado;
import com.ayudartrazable.api.vecinos.dto.VecinosDto.Perfil;
import com.ayudartrazable.api.vecinos.dto.VecinosDto.ReporteRecibido;
import com.ayudartrazable.api.vecinos.dto.VecinosDto.TelefonosEmergencia;
import com.ayudartrazable.api.vecinos.dto.VecinosDto.VecinoVisible;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class VecinosServicio {

    private final VecinosRepositorio repositorio;
    private final Geocodificador geocodificador;

    public VecinosServicio(VecinosRepositorio repositorio, Geocodificador geocodificador) {
        this.repositorio = repositorio;
        this.geocodificador = geocodificador;
    }

    public Perfil perfil(UUID usuario) {
        return repositorio.perfil(usuario);
    }

    public RespuestaDomicilio cambiarDomicilio(UUID usuario, double latitud, double longitud) {
        String direccion = geocodificador.direccion(latitud, longitud).orElse(null);
        return repositorio.cambiarDomicilio(usuario, latitud, longitud, direccion);
    }

    public DispositivoRegistrado registrarDispositivo(UUID usuario, UUID sesion, String token) {
        return repositorio.registrarDispositivo(usuario, sesion, token);
    }

    public TelefonosEmergencia telefonos(UUID usuario) {
        return repositorio.telefonos(usuario);
    }

    public ReporteRecibido reportarDatos(UUID usuario, String descripcion) {
        return repositorio.reportarDatos(usuario, descripcion);
    }

    public List<VecinoVisible> vecinosDelCuadrante(UUID usuario) {
        return repositorio.vecinosDelCuadrante(usuario);
    }
}
