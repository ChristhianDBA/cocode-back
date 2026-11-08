package com.ayudartrazable.api.comun.logs;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

public class MensajeEnmascarado extends MessageConverter {

    @Override
    public String convert(ILoggingEvent evento) {
        return Enmascarador.enmascarar(super.convert(evento));
    }
}
