package com.ayudartrazable.api.comun.seguridad;

import com.ayudartrazable.api.comun.errores.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableMethodSecurity
public class ConfiguracionSeguridad {

    static final String[] RUTAS_PUBLICAS = {
            "/api/v1/auth/dni",
            "/api/v1/auth/reniec/**",
            "/api/v1/auth/tramite",
            "/api/v1/auth/codigo",
            "/api/v1/auth/codigo/verificar",
            "/api/v1/auth/alta",
            "/api/v1/auth/alta/domicilio",
            "/api/v1/auth/ingreso",
            "/api/v1/auth/ingreso/**",
            "/api/v1/auth/ingreso/verificar",
            "/api/v1/auth/renovar",
            "/api/v1/auth/recuperacion/**",
            "/api/v1/geo/resolver-pin",
            "/ws/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api/v1/publicaciones/**",
            "/api/v1/actividad/**",
            "/api/v1/chat/**",
            "/api/v1/geo/mapa/pines",
            "/api/v1/vecinos/*/perfil",
            "/api/v1/vecinos/*/resenas",
            "/api/v1/vecinos/*/visto-bueno",
            "/api/v1/vecinos/me/ocupacion",
            "/api/v1/emergencias/directorio-local",
            "/api/v1/emergencias/tablero/**"
    };

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Bean
    SecurityFilterChain cadenaDeSeguridad(HttpSecurity http, ValidadorSesion validador) throws Exception {
        ConvertidorAutenticacion convertidor = new ConvertidorAutenticacion(validador);
        http
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(RUTAS_PUBLICAS).permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/emergencias/*/estado").hasAnyRole("COORDINADOR", "SERENAZGO")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(convertidor))
                        .authenticationEntryPoint(NO_AUTENTICADO)
                        .accessDeniedHandler(SIN_PERMISO))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(NO_AUTENTICADO)
                        .accessDeniedHandler(SIN_PERMISO));
        return http.build();
    }

    private static final AuthenticationEntryPoint NO_AUTENTICADO = (req, res, ex) -> escribir(res, 401,
            ApiError.de("NO_AUTENTICADO", "Tu sesión no es válida. Inicia sesión nuevamente"));

    private static final AccessDeniedHandler SIN_PERMISO = (req, res, ex) -> escribir(res, 403,
            ApiError.de("SIN_PERMISO", "No tienes permiso para realizar esta acción"));

    @Bean
    JwtDecoder decodificadorJwt(@Value("${seguridad.jwt.secreto}") String secreto) {
        return NimbusJwtDecoder.withSecretKey(clave(secreto)).macAlgorithm(MacAlgorithm.HS256).build();
    }

    static SecretKey clave(String secreto) {
        byte[] bytes = secreto == null ? new byte[0] : secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("seguridad.jwt.secreto debe tener al menos 32 bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    private static final void escribir(HttpServletResponse respuesta, int estado, ApiError error) throws IOException {
        respuesta.setStatus(estado);
        respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.getWriter().write(JSON.writeValueAsString(error));
    }
}
