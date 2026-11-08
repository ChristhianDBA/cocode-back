package com.ayudartrazable.api.comun.seguridad;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

@Component
public class EmisorTokens {

    public static final Duration VIGENCIA_ACCESO = Duration.ofMinutes(15);

    private final JwtEncoder codificador;

    public EmisorTokens(@Value("${seguridad.jwt.secreto}") String secreto) {
        this.codificador = new NimbusJwtEncoder(new ImmutableSecret<>(ConfiguracionSeguridad.clave(secreto)));
    }

    public String emitirAcceso(UUID sesionId) {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(sesionId.toString())
                .issuedAt(ahora)
                .expiresAt(ahora.plus(VIGENCIA_ACCESO))
                .build();
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        return codificador.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
    }
}
