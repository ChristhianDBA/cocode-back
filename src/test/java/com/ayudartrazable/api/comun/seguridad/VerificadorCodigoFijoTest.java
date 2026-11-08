package com.ayudartrazable.api.comun.seguridad;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ayudartrazable.api.comun.bd.LlamadorSp;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.node.BooleanNode;

class VerificadorCodigoFijoTest {

    private final LlamadorSp sp = mock(LlamadorSp.class);

    private VerificadorCodigoFijo verificador(String... perfiles) {
        MockEnvironment entorno = new MockEnvironment();
        entorno.setActiveProfiles(perfiles);
        return new VerificadorCodigoFijo(entorno, sp);
    }

    @Test
    void A6_fueraDeDevConCodigoFijo_noArranca() {
        when(sp.llamar("fn_codigo_fijo_activo")).thenReturn(BooleanNode.TRUE);
        assertThatThrownBy(() -> verificador("produccion").verificar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("codigo_fijo");
        assertThatThrownBy(() -> verificador().verificar()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void A6_fueraDeDevSinCodigoFijo_arranca() {
        when(sp.llamar("fn_codigo_fijo_activo")).thenReturn(BooleanNode.FALSE);
        assertThatCode(() -> verificador("produccion").verificar()).doesNotThrowAnyException();
    }

    @Test
    void A6_enDevOPruebas_noSeConsulta() {
        verificador("dev").verificar();
        verificador("test").verificar();
        verify(sp, never()).llamar(anyString());
    }
}
