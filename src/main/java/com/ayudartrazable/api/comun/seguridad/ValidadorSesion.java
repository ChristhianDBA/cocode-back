package com.ayudartrazable.api.comun.seguridad;

import java.util.Optional;
import java.util.UUID;

public interface ValidadorSesion {

    Optional<UsuarioActual> validar(UUID sesionId);
}
