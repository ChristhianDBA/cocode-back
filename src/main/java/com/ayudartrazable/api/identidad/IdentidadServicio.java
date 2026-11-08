package com.ayudartrazable.api.identidad;

import com.ayudartrazable.api.comun.errores.ExcepcionNegocio;
import com.ayudartrazable.api.comun.seguridad.EmisorTokens;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.CodigoEmitido;
import com.ayudartrazable.api.integraciones.mensajeria.EnvioCodigo;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.EstadoTramite;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.SolicitudDni;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.RespuestaCodigo;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.RespuestaDomicilio;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.RespuestaIngreso;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.RespuestaSesion;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.RespuestaTramite;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.RespuestaVerificacion;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.ResultadoIngreso;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.SesionCreada;
import com.ayudartrazable.api.integraciones.geocodificacion.Geocodificador;
import com.ayudartrazable.api.integraciones.mensajeria.Canal;
import com.ayudartrazable.api.integraciones.registrocivil.ConsultaRegistroCivil;
import com.ayudartrazable.api.integraciones.registrocivil.DatosCiviles;
import com.ayudartrazable.api.comun.errores.ExcepcionServicioNoDisponible;
import com.ayudartrazable.api.identidad.dto.IdentidadDto.RecuperacionPendiente;
import com.ayudartrazable.api.integraciones.push.NotificacionPush;
import com.ayudartrazable.api.notificaciones.DifusionAlertas;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Service;

@Service
public class IdentidadServicio {

    private final IdentidadRepositorio repositorio;
    private final ConsultaRegistroCivil registroCivil;
    private final EnvioCodigo mensajeria;
    private final Geocodificador geocodificador;
    private final EmisorTokens tokens;
    private final DifusionAlertas difusion;
    private final JsonMapper json;

    public IdentidadServicio(IdentidadRepositorio repositorio, ConsultaRegistroCivil registroCivil,
            EnvioCodigo mensajeria, Geocodificador geocodificador,
            EmisorTokens tokens, DifusionAlertas difusion, JsonMapper json) {
        this.difusion = difusion;
        this.json = json;
        this.repositorio = repositorio;
        this.registroCivil = registroCivil;
        this.mensajeria = mensajeria;
        this.geocodificador = geocodificador;
        this.tokens = tokens;
    }

    public RespuestaTramite iniciarTramite(String proposito, SolicitudDni solicitud) {
        DatosCiviles datos = registroCivil.consultar(solicitud.dni())
                .orElseThrow(() -> new ExcepcionNegocio(404, "DNI_NO_ENCONTRADO",
                        "No encontramos este DNI. Revisa el número e inténtalo de nuevo"))
                .completarCon(solicitud.fechaNacimiento(), solicitud.sexo());
        return repositorio.iniciarTramite(proposito, datos, solicitud.origen().name());
    }

    public EstadoTramite estadoTramite(String token) {
        return repositorio.estadoTramite(token);
    }

    public RespuestaCodigo solicitarCodigo(String token, String celular, Canal canal) {
        CodigoEmitido envio = repositorio.solicitarCodigoTramite(token, celular, canal);
        enviar(envio.codigoId(), envio.celular(), envio.codigo(), envio.canal(), envio.minutosDeVigencia());
        return new RespuestaCodigo(envio.venceEnSegundos(), envio.reenvioEnSegundos());
    }

    public RespuestaVerificacion verificarCodigo(String token, String codigo) {
        return repositorio.verificarCodigoTramite(token, codigo);
    }

    public RespuestaDomicilio guardarDomicilio(String token, double latitud, double longitud) {
        String direccion = geocodificador.direccion(latitud, longitud).orElse(null);
        return repositorio.guardarDomicilioAlta(token, latitud, longitud, direccion);
    }

    public RespuestaSesion completarAlta(String token, Boolean aceptaTratamiento) {
        return sesion(repositorio.completarAlta(token, aceptaTratamiento));
    }

    public RespuestaIngreso solicitarIngreso(String dni, Canal canal) {
        ResultadoIngreso resultado = repositorio.solicitarIngreso(dni, canal);
        if (resultado.envio() != null) {
            enviar(UUID.fromString(resultado.tokenIngreso()), resultado.envio().celular(), resultado.envio().codigo(),
                    resultado.envio().canal(), resultado.envio().minutosDeVigencia());
        }
        return new RespuestaIngreso(resultado.tokenIngreso(), resultado.celularEnmascarado(),
                resultado.venceEnSegundos(), resultado.reenvioEnSegundos());
    }

    public RespuestaSesion verificarIngreso(String tokenIngreso, String codigo) {
        return sesion(repositorio.verificarIngreso(tokenIngreso, codigo));
    }

    public RespuestaSesion renovar(String tokenRenovacion) {
        return sesion(repositorio.renovar(tokenRenovacion));
    }

    public void cerrar(UUID sesionId) {
        repositorio.cerrar(sesionId);
    }

    public Object completarRecuperacion(String token) {
        JsonNode r = repositorio.completarRecuperacion(token);
        if (!r.path("pendiente").asBoolean(false)) {
            return sesion(json.treeToValue(r, SesionCreada.class));
        }
        UUID usuario = UUID.fromString(r.get("usuarioId").asString());
        try {
            mensajeria.avisar(r.get("celularAnterior").asString(), r.get("avisoTitular").asString());
        } catch (RuntimeException e) {
            repositorio.cancelarRecuperacion(usuario);
            throw new ExcepcionServicioNoDisponible(
                    "No pudimos avisar al celular anterior de la cuenta. Intenta más tarde", e);
        }
        List<String> dispositivos = new ArrayList<>();
        r.path("dispositivos").forEach(d -> dispositivos.add(d.asString()));
        difusion.enviarPush(dispositivos, new NotificacionPush("Solicitud de recuperación de tu cuenta",
                r.get("avisoTitular").asString(), Map.of("evento", "RECUPERACION_SOLICITADA")));
        return new RecuperacionPendiente(true, r.get("aplicaEn").asString(), r.get("mensaje").asString());
    }

    public JsonNode cancelarRecuperacion(UUID usuario) {
        return repositorio.cancelarRecuperacion(usuario);
    }

    private void enviar(UUID codigoId, String celular, String codigo, Canal canal, int minutos) {
        try {
            mensajeria.enviar(celular, codigo, canal, minutos);
        } catch (RuntimeException e) {
            repositorio.anularCodigo(codigoId);
            throw e;
        }
    }

    private RespuestaSesion sesion(SesionCreada creada) {
        return new RespuestaSesion(tokens.emitirAcceso(creada.sesionId()), creada.tokenRenovacion(),
                EmisorTokens.VIGENCIA_ACCESO.toSeconds(), creada.mensajeBienvenida(), creada.insignia());
    }
}
