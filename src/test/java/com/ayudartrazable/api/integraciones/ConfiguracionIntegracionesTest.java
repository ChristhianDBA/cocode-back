package com.ayudartrazable.api.integraciones;

import static org.assertj.core.api.Assertions.assertThat;

import com.ayudartrazable.api.integraciones.geocodificacion.GeocodificadorGoogle;
import com.ayudartrazable.api.integraciones.geocodificacion.GeocodificadorSimulado;
import com.ayudartrazable.api.integraciones.mensajeria.EnvioCodigoSimulado;
import com.ayudartrazable.api.integraciones.mensajeria.EnvioCodigoTwilio;
import com.ayudartrazable.api.integraciones.push.EnvioPushFcm;
import com.ayudartrazable.api.integraciones.push.EnvioPushSimulado;
import com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilReniec;
import com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ConfiguracionIntegracionesTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(ConfiguracionIntegraciones.class);

    @Test
    void modoSimulado_registraSimuladores() {
        contexto.withPropertyValues("integraciones.modo=simulado").run(c -> {
            assertThat(c).hasSingleBean(RegistroCivilSimulado.class);
            assertThat(c).hasSingleBean(EnvioCodigoSimulado.class);
            assertThat(c).hasSingleBean(GeocodificadorSimulado.class);
            assertThat(c).hasSingleBean(EnvioPushSimulado.class);
        });
    }

    @Test
    void modoReal_registraAdaptadores() {
        contexto.withPropertyValues(
                "integraciones.modo=real",
                "integraciones.registro-civil.url=https://registro.test/v1/reniec/dni?numero={dni}",
                "integraciones.registro-civil.token=t",
                "integraciones.twilio.cuenta=AC1",
                "integraciones.twilio.clave=c",
                "integraciones.twilio.remitente-sms=+1555",
                "integraciones.twilio.remitente-whatsapp=+1555",
                "integraciones.mapas.clave=k",
                "integraciones.fcm.proyecto=p",
                "integraciones.fcm.credenciales=no-existe.json").run(c -> {
            assertThat(c).hasSingleBean(RegistroCivilReniec.class);
            assertThat(c).hasSingleBean(EnvioCodigoTwilio.class);
            assertThat(c).hasSingleBean(GeocodificadorGoogle.class);
            assertThat(c).hasSingleBean(EnvioPushFcm.class);
        });
    }

    @Test
    void T075_registroCivilRealConElRestoSimulado() {
        contexto.withPropertyValues(
                "integraciones.modo=simulado",
                "integraciones.registro-civil.modo=real",
                "integraciones.registro-civil.url=https://registro.test/v1/reniec/dni?numero={dni}",
                "integraciones.registro-civil.token=t").run(c -> {
            assertThat(c).hasSingleBean(RegistroCivilReniec.class);
            assertThat(c).doesNotHaveBean(RegistroCivilSimulado.class);
            assertThat(c).hasSingleBean(EnvioCodigoSimulado.class);
            assertThat(c).hasSingleBean(EnvioPushSimulado.class);
        });
    }

    @Test
    void sinModo_noArrancaParaEvitarUsarSimuladoresPorError() {
        contexto.run(c -> assertThat(c).hasFailed());
    }
}
