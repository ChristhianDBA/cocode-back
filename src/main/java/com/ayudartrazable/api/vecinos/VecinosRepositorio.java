package com.ayudartrazable.api.vecinos;

import com.ayudartrazable.api.comun.bd.LlamadorSp;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.RespuestaDomicilio;
import com.ayudartrazable.api.vecinos.dto.VecinosDto.DispositivoRegistrado;
import com.ayudartrazable.api.vecinos.dto.VecinosDto.Perfil;
import com.ayudartrazable.api.vecinos.dto.VecinosDto.ReporteRecibido;
import com.ayudartrazable.api.vecinos.dto.VecinosDto.TelefonosEmergencia;
import com.ayudartrazable.api.vecinos.dto.VecinosDto.VecinoVisible;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class VecinosRepositorio {

    private final LlamadorSp sp;

    public VecinosRepositorio(LlamadorSp sp) {
        this.sp = sp;
    }

    public Perfil perfil(UUID usuario) {
        return sp.llamar(Perfil.class, "fn_perfil", usuario);
    }

    public RespuestaDomicilio cambiarDomicilio(UUID usuario, double latitud, double longitud, String direccion) {
        return sp.llamar(RespuestaDomicilio.class, "sp_domicilio_cambiar", usuario, latitud, longitud, direccion);
    }

    public DispositivoRegistrado registrarDispositivo(UUID usuario, UUID sesion, String token) {
        return sp.llamar(DispositivoRegistrado.class, "sp_dispositivo_registrar", usuario, sesion, token);
    }

    public void invalidarDispositivo(String token) {
        sp.llamar("sp_dispositivo_invalidar", token);
    }

    public TelefonosEmergencia telefonos(UUID usuario) {
        return sp.llamar(TelefonosEmergencia.class, "fn_telefonos_emergencia", usuario);
    }

    public ReporteRecibido reportarDatos(UUID usuario, String descripcion) {
        return sp.llamar(ReporteRecibido.class, "sp_reporte_datos_civiles", usuario, descripcion);
    }

    public List<VecinoVisible> vecinosDelCuadrante(UUID usuario) {
        return Arrays.asList(sp.llamar(VecinoVisible[].class, "fn_vecinos_cuadrante", usuario));
    }
}
