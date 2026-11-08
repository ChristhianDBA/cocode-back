package com.ayudartrazable.api.soporte;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class ExtensionBaseDeDatos implements BeforeAllCallback, BeforeEachCallback {

    @Override
    public void beforeAll(ExtensionContext contexto) {
        BaseDeDatosDePrueba.recrearUnaVez();
    }

    @Override
    public void beforeEach(ExtensionContext contexto) {
        BaseDeDatosDePrueba.reiniciarDatos();
    }
}
