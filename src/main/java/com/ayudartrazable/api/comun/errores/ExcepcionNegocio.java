package com.ayudartrazable.api.comun.errores;

public class ExcepcionNegocio extends RuntimeException {

    private final int estado;
    private final String codigo;

    public ExcepcionNegocio(int estado, String codigo, String mensaje) {
        super(mensaje);
        this.estado = estado;
        this.codigo = codigo;
    }

    public int estado() {
        return estado;
    }

    public String codigo() {
        return codigo;
    }
}
