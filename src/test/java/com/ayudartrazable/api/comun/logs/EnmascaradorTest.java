package com.ayudartrazable.api.comun.logs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class EnmascaradorTest {

    private static final Logger LOG = LoggerFactory.getLogger(EnmascaradorTest.class);

    @Test
    void enmascaraDni() {
        assertThat(Enmascarador.enmascarar("dni 45678912 recibido")).isEqualTo("dni ******12 recibido");
    }

    @Test
    void enmascaraCelularConYSinPrefijo() {
        assertThat(Enmascarador.enmascarar("cel 987654321")).isEqualTo("cel *******21");
        assertThat(Enmascarador.enmascarar("cel +51987654321")).isEqualTo("cel *******21");
    }

    @Test
    void enmascaraCodigoDeVerificacion() {
        assertThat(Enmascarador.enmascarar("codigo=1234")).isEqualTo("codigo=****");
        assertThat(Enmascarador.enmascarar("\"codigo\":\"5678\"")).isEqualTo("\"codigo\":\"****\"");
    }

    @Test
    void enmascaraTokens() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.abcDEF123_-xyz";
        assertThat(Enmascarador.enmascarar("Bearer " + jwt)).doesNotContain(jwt).contains("[TOKEN]");
        String opaco = "Qx7vK2mN9pL4rT8wY1zB5cF3hJ6kM0nP2sV4xA7dG9e";
        assertThat(Enmascarador.enmascarar("renovacion " + opaco)).doesNotContain(opaco);
    }

    @Test
    void enmascaraCoordenadas() {
        assertThat(Enmascarador.enmascarar("punto -12.090512, -76.990234"))
                .isEqualTo("punto -12.09***, -76.99***");
    }

    @Test
    void noOcultaNombresDeMetodosEnTrazas() {
        String traza = "at com.x.ManejadorErroresTest.errorDeBdNoControlado_devuelve500Generico(ManejadorErroresTest.java:115)";
        assertThat(Enmascarador.enmascarar(traza)).isEqualTo(traza);
    }

    @Test
    void noOcultaIdentificadoresUuid() {
        String id = "alerta a1b2c3d4-e5f6-7890-abcd-222222222222";
        assertThat(Enmascarador.enmascarar(id)).isEqualTo(id);
    }

    @Test
    void noAlteraTextoSinDatosSensibles() {
        assertThat(Enmascarador.enmascarar("alerta 42 creada en 120 ms")).isEqualTo("alerta 42 creada en 120 ms");
    }

    @Test
    void laSalidaDeLogsYaVieneEnmascarada(CapturedOutput salida) {
        LOG.info("alta dni={} celular={} punto={}", "45678912", "987654321", "-12.090512");
        LOG.error("fallo", new IllegalStateException("llave duplicada (dni)=(45678912)"));
        assertThat(salida.getAll())
                .doesNotContain("45678912")
                .doesNotContain("987654321")
                .doesNotContain("-12.090512");
    }
}
