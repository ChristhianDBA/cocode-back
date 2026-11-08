package com.ayudartrazable.api.identidad;

import com.ayudartrazable.api.comun.bd.LlamadorSp;
import com.ayudartrazable.api.comun.seguridad.UsuarioActual;
import com.ayudartrazable.api.comun.seguridad.ValidadorSesion;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.CodigoEmitido;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.EstadoTramite;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.RespuestaDomicilio;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.RespuestaTramite;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.RespuestaVerificacion;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.ResultadoIngreso;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.SesionCreada;
import com.ayudartrazable.api.integraciones.mensajeria.Canal;
import com.ayudartrazable.api.integraciones.registrocivil.DatosCiviles;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;

@Repository
public class IdentidadRepositorio implements ValidadorSesion {

    private final LlamadorSp sp;

    public IdentidadRepositorio(LlamadorSp sp) {
        this.sp = sp;
    }

    public RespuestaTramite iniciarTramite(String proposito, DatosCiviles d, String origen) {
        return sp.llamar(RespuestaTramite.class, "sp_tramite_iniciar", proposito, d.dni(), d.nombres(),
                d.apellidoPaterno(), d.apellidoMaterno(), d.fechaNacimiento(), d.sexo(), origen);
    }

    public EstadoTramite estadoTramite(String token) {
        return sp.llamar(EstadoTramite.class, "fn_tramite_estado", token);
    }

    public CodigoEmitido solicitarCodigoTramite(String token, String celular, Canal canal) {
        return sp.llamar(CodigoEmitido.class, "sp_tramite_solicitar_codigo", token, celular, canal.name());
    }

    public RespuestaVerificacion verificarCodigoTramite(String token, String codigo) {
        return sp.llamar(RespuestaVerificacion.class, "sp_tramite_verificar_codigo", token, codigo);
    }

    public void anularCodigo(UUID codigoId) {
        sp.llamar("sp_codigo_anular", codigoId);
    }

    public RespuestaDomicilio guardarDomicilioAlta(String token, double latitud, double longitud, String direccion) {
        return sp.llamar(RespuestaDomicilio.class, "sp_alta_guardar_domicilio", token, latitud, longitud, direccion);
    }

    public SesionCreada completarAlta(String token, Boolean aceptaTratamiento) {
        return sp.llamar(SesionCreada.class, "sp_alta_completar", token, aceptaTratamiento);
    }

    public ResultadoIngreso solicitarIngreso(String dni, Canal canal) {
        return sp.llamar(ResultadoIngreso.class, "sp_ingreso_solicitar", dni, canal.name());
    }

    public SesionCreada verificarIngreso(String tokenIngreso, String codigo) {
        return sp.llamar(SesionCreada.class, "sp_ingreso_verificar", tokenIngreso, codigo);
    }

    public SesionCreada renovar(String tokenRenovacion) {
        return sp.llamar(SesionCreada.class, "sp_sesion_renovar", tokenRenovacion);
    }

    public void cerrar(UUID sesionId) {
        sp.llamar("sp_sesion_cerrar", sesionId);
    }

    public JsonNode cancelarRecuperacion(UUID usuario) {
        return sp.llamar("sp_recuperacion_cancelar", usuario);
    }

    public JsonNode completarRecuperacion(String token) {
        return sp.llamar("sp_recuperacion_completar", token);
    }

    @Override
    public Optional<UsuarioActual> validar(UUID sesionId) {
        return Optional.ofNullable(sp.llamar(UsuarioActual.class, "fn_validar_sesion", sesionId));
    }

    public boolean tramiteVigente(String token) {
        try {
            sp.llamar("fn_tramite_estado", token);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
