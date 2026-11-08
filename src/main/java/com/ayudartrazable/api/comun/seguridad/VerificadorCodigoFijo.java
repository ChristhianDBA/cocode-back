package com.ayudartrazable.api.comun.seguridad;

import com.ayudartrazable.api.comun.bd.LlamadorSp;
import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class VerificadorCodigoFijo {

    private final Environment entorno;
    private final LlamadorSp sp;

    public VerificadorCodigoFijo(Environment entorno, LlamadorSp sp) {
        this.entorno = entorno;
        this.sp = sp;
    }

    @PostConstruct
    public void verificar() {
        if (entorno.acceptsProfiles(Profiles.of("dev | test"))) {
            return;
        }
        JsonNode activo = sp.llamar("fn_codigo_fijo_activo");
        if (activo != null && activo.asBoolean(false)) {
            throw new IllegalStateException(
                    "La base tiene activo el parámetro codigo_fijo, que solo se permite en desarrollo");
        }
    }
}
