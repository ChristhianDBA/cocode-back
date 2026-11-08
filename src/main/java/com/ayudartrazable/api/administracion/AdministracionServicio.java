package com.ayudartrazable.api.administracion;

import com.ayudartrazable.api.comun.errores.ExcepcionNegocio;
import com.ayudartrazable.api.integraciones.push.NotificacionPush;
import com.ayudartrazable.api.integraciones.registrocivil.ConsultaRegistroCivil;
import com.ayudartrazable.api.integraciones.registrocivil.DatosCiviles;
import com.ayudartrazable.api.notificaciones.DifusionAlertas;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class AdministracionServicio {

    static final String CUADRANTE_DESHABILITADO = "CUADRANTE_DESHABILITADO";

    private final AdministracionRepositorio repositorio;
    private final ConsultaRegistroCivil registroCivil;
    private final DifusionAlertas difusion;
    private final JsonMapper json;

    public AdministracionServicio(AdministracionRepositorio repositorio, ConsultaRegistroCivil registroCivil,
            DifusionAlertas difusion, JsonMapper json) {
        this.repositorio = repositorio;
        this.registroCivil = registroCivil;
        this.difusion = difusion;
        this.json = json;
    }

    public JsonNode buscarUsuario(UUID admin, String dni) {
        JsonNode usuario = repositorio.buscarUsuario(admin, dni);
        if (usuario == null) {
            throw new ExcepcionNegocio(404, "USUARIO_NO_ENCONTRADO", "No hay un usuario registrado con ese DNI");
        }
        return usuario;
    }

    public JsonNode asignarCoordinador(UUID admin, UUID usuario, List<UUID> cuadrantes) {
        return repositorio.asignarCoordinador(admin, usuario, cuadrantes);
    }

    public JsonNode retirarCoordinador(UUID admin, UUID usuario, UUID cuadrante) {
        return repositorio.retirarCoordinador(admin, usuario, cuadrante);
    }

    public JsonNode altaSerenazgo(UUID admin, String dni, String celular, int distrito,
            java.time.LocalDate fechaNacimiento, String sexo) {
        DatosCiviles datos = registroCivil.consultar(dni)
                .orElseThrow(() -> new ExcepcionNegocio(404, "DNI_NO_ENCONTRADO",
                        "No encontramos este DNI. Revisa el número e inténtalo de nuevo"))
                .completarCon(fechaNacimiento, sexo);
        return repositorio.altaSerenazgo(admin, datos, celular, distrito);
    }

    public JsonNode desactivarSerenazgo(UUID admin, UUID usuario) {
        return repositorio.desactivarSerenazgo(admin, usuario);
    }

    public JsonNode avisos(UUID admin) {
        return repositorio.avisos(admin);
    }

    public JsonNode levantarSuspension(UUID admin, UUID suspension) {
        return repositorio.levantarSuspension(admin, suspension);
    }

    public JsonNode deshabilitarCuadrante(UUID admin, UUID cuadrante) {
        JsonNode r = repositorio.deshabilitarCuadrante(admin, cuadrante);
        difusion.avisarCuadrante(cuadrante, CUADRANTE_DESHABILITADO,
                json.valueToTree(Map.of("cuadranteId", cuadrante.toString(), "alertasCerradas", r.get("alertasCerradas"))));
        difusion.enviarPush(textos(r.get("dispositivosSinCuadrante")), new NotificacionPush("Tu zona aún no tiene cobertura",
                "Tu cuadrante fue deshabilitado. Te avisaremos cuando vuelva a estar disponible",
                Map.of("evento", CUADRANTE_DESHABILITADO)));
        difusion.enviarPush(textos(r.get("dispositivosReasignados")), new NotificacionPush("Cambió tu cuadrante",
                "Tu cuadrante anterior fue deshabilitado. Ya formas parte de otro cuadrante cercano",
                Map.of("evento", "CUADRANTE_REASIGNADO")));
        return sinDispositivos(r);
    }

    public JsonNode habilitarCuadrante(UUID admin, UUID cuadrante) {
        JsonNode r = repositorio.habilitarCuadrante(admin, cuadrante);
        difusion.enviarPush(textos(r.get("dispositivos")), new NotificacionPush("Tu zona ya tiene cobertura",
                "Ya eres parte de un cuadrante. Entra a tu barrio", Map.of("evento", "CUADRANTE_HABILITADO")));
        return sinDispositivos(r);
    }

    private static JsonNode sinDispositivos(JsonNode r) {
        ObjectNode copia = ((ObjectNode) r).deepCopy();
        copia.remove(List.of("dispositivos", "dispositivosSinCuadrante", "dispositivosReasignados"));
        return copia;
    }

    private static List<String> textos(JsonNode arreglo) {
        List<String> lista = new ArrayList<>();
        if (arreglo != null) {
            arreglo.forEach(n -> lista.add(n.asString()));
        }
        return lista;
    }
}
