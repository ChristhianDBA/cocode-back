package com.ayudartrazable.api.comun.bd;

import com.ayudartrazable.api.comun.errores.ExcepcionNegocio;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class LlamadorSp {

    private static final Pattern NOMBRE = Pattern.compile("^(sp|fn)_[a-z0-9_]+$");

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public LlamadorSp(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public JsonNode llamar(String funcion, Object... argumentos) {
        if (!NOMBRE.matcher(funcion).matches()) {
            throw new IllegalArgumentException("Nombre de función no permitido: " + funcion);
        }
        String marcadores = argumentos.length == 0 ? "" : "?" + ", ?".repeat(argumentos.length - 1);
        String resultado = jdbc.queryForObject("select " + funcion + "(" + marcadores + ")::text", String.class, argumentos);
        if (resultado == null) {
            return null;
        }
        JsonNode nodo = json.readTree(resultado);
        if (nodo.isObject() && nodo.has("error")) {
            JsonNode error = nodo.get("error");
            throw new ExcepcionNegocio(error.get("estado").asInt(), error.get("codigo").asString(),
                    error.get("mensaje").asString());
        }
        return nodo;
    }

    public <T> T llamar(Class<T> tipo, String funcion, Object... argumentos) {
        JsonNode nodo = llamar(funcion, argumentos);
        return nodo == null ? null : json.treeToValue(nodo, tipo);
    }
}
