package com.ayudartrazable.api.integraciones.registrocivil;

import java.util.Optional;

public interface ConsultaRegistroCivil {

    Optional<DatosCiviles> consultar(String dni);
}
