package com.ayudartrazable.api.integraciones;

import com.ayudartrazable.api.integraciones.geocodificacion.Geocodificador;
import com.ayudartrazable.api.integraciones.geocodificacion.GeocodificadorGoogle;
import com.ayudartrazable.api.integraciones.geocodificacion.GeocodificadorSimulado;
import com.ayudartrazable.api.integraciones.mensajeria.EnvioCodigo;
import com.ayudartrazable.api.integraciones.mensajeria.EnvioCodigoSimulado;
import com.ayudartrazable.api.integraciones.mensajeria.EnvioCodigoTwilio;
import com.ayudartrazable.api.integraciones.push.EnvioPush;
import com.ayudartrazable.api.integraciones.push.EnvioPushFcm;
import com.ayudartrazable.api.integraciones.push.EnvioPushSimulado;
import com.ayudartrazable.api.integraciones.registrocivil.ConsultaRegistroCivil;
import com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilReniec;
import com.ayudartrazable.api.integraciones.registrocivil.RegistroCivilSimulado;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ConfiguracionIntegraciones {

    ConfiguracionIntegraciones(Environment entorno) {
        String modo = entorno.getProperty("integraciones.modo");
        if (!"simulado".equals(modo) && !"real".equals(modo)) {
            throw new IllegalStateException("integraciones.modo debe ser 'simulado' o 'real'");
        }
    }

    static RestClient.Builder cliente(String urlBase) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(Duration.ofSeconds(5));
        RestClient.Builder builder = RestClient.builder().requestFactory(fabrica);
        return urlBase == null ? builder : builder.baseUrl(urlBase);
    }

    @Configuration
    static class RegistroCivil {

        @Bean
        @ConditionalOnExpression("'${integraciones.registro-civil.modo:${integraciones.modo:}}' == 'simulado'")
        RegistroCivilSimulado registroCivilSimulado() {
            return new RegistroCivilSimulado();
        }

        @Bean
        @ConditionalOnExpression("'${integraciones.registro-civil.modo:${integraciones.modo:}}' == 'real'")
        ConsultaRegistroCivil registroCivilReal(@Value("${integraciones.registro-civil.url}") String url,
                @Value("${integraciones.registro-civil.token}") String token) {
            return new RegistroCivilReniec(cliente(null), url, token);
        }
    }

    @Configuration
    @ConditionalOnProperty(name = "integraciones.modo", havingValue = "simulado")
    static class Simuladas {

        @Bean
        EnvioCodigoSimulado envioCodigo() {
            return new EnvioCodigoSimulado();
        }

        @Bean
        GeocodificadorSimulado geocodificador() {
            return new GeocodificadorSimulado();
        }

        @Bean
        EnvioPushSimulado envioPush() {
            return new EnvioPushSimulado();
        }
    }

    @Configuration
    @ConditionalOnProperty(name = "integraciones.modo", havingValue = "real")
    static class Reales {

        @Bean
        EnvioCodigo envioCodigo(@Value("${integraciones.twilio.cuenta}") String cuenta,
                @Value("${integraciones.twilio.clave}") String clave,
                @Value("${integraciones.twilio.remitente-sms}") String sms,
                @Value("${integraciones.twilio.remitente-whatsapp}") String whatsapp) {
            return new EnvioCodigoTwilio(cliente("https://api.twilio.com"), cuenta, clave, sms, whatsapp);
        }

        @Bean
        Geocodificador geocodificador(@Value("${integraciones.mapas.clave}") String clave) {
            return new GeocodificadorGoogle(cliente("https://maps.googleapis.com"), clave);
        }

        @Bean
        EnvioPush envioPush(@Value("${integraciones.fcm.proyecto}") String proyecto,
                @Value("${integraciones.fcm.credenciales}") String rutaCredenciales) {
            AtomicReference<GoogleCredentials> cache = new AtomicReference<>();
            return new EnvioPushFcm(cliente("https://fcm.googleapis.com"), proyecto, () -> {
                try {
                    GoogleCredentials credenciales = cache.updateAndGet(c -> c != null ? c : credenciales(rutaCredenciales));
                    credenciales.refreshIfExpired();
                    return credenciales.getAccessToken().getTokenValue();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }

        private static GoogleCredentials credenciales(String ruta) {
            try (InputStream entrada = new FileInputStream(ruta)) {
                return GoogleCredentials.fromStream(entrada)
                        .createScoped(List.of("https://www.googleapis.com/auth/firebase.messaging"));
            } catch (IOException e) {
                throw new UncheckedIOException("No se pudo leer integraciones.fcm.credenciales", e);
            }
        }
    }
}
