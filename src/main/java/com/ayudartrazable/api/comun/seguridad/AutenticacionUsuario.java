package com.ayudartrazable.api.comun.seguridad;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class AutenticacionUsuario extends AbstractAuthenticationToken {

    private final UsuarioActual usuario;

    public AutenticacionUsuario(UsuarioActual usuario) {
        super(usuario.roles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList());
        this.usuario = usuario;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public UsuarioActual getPrincipal() {
        return usuario;
    }

    @Override
    public String getName() {
        return usuario.usuarioId().toString();
    }
}
