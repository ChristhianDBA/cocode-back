package com.ayudartrazable.api.comun.seguridad;

import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

public class ConvertidorAutenticacion implements Converter<Jwt, AbstractAuthenticationToken> {

    private final ValidadorSesion validador;

    public ConvertidorAutenticacion(ValidadorSesion validador) {
        this.validador = validador;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID sesion;
        try {
            sesion = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidBearerTokenException("Sesión no válida");
        }
        return validador.validar(sesion)
                .map(AutenticacionUsuario::new)
                .orElseThrow(() -> new InvalidBearerTokenException("Sesión no válida"));
    }
}
