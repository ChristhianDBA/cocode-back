package com.ayudartrazable.api.comun.seguridad;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

public final class TokensDePrueba {

    private TokensDePrueba() {
    }

    public static String firmar(String secreto, String sesion, long segundosDeVida) {
        try {
            Instant ahora = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(sesion)
                    .issueTime(Date.from(ahora.minusSeconds(Math.max(0, -segundosDeVida) + 1)))
                    .expirationTime(Date.from(ahora.plusSeconds(segundosDeVida)))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secreto.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
