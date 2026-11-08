package com.ayudartrazable.api.comun.logs;

import ch.qos.logback.classic.pattern.ExtendedThrowableProxyConverter;
import ch.qos.logback.classic.spi.IThrowableProxy;

public class ExcepcionEnmascarada extends ExtendedThrowableProxyConverter {

    @Override
    protected String throwableProxyToString(IThrowableProxy proxy) {
        return Enmascarador.enmascarar(super.throwableProxyToString(proxy));
    }
}
