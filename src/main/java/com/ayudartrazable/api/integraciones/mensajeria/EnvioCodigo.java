package com.ayudartrazable.api.integraciones.mensajeria;

public interface EnvioCodigo {

    void enviar(String celular, String codigo, Canal canal, int minutosDeVigencia);

    void avisar(String celular, String mensaje);
}
