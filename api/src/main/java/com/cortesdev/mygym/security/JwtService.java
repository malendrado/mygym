package com.cortesdev.mygym.security;

import com.cortesdev.mygym.models.Role;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Issues and validates the app's own session JWT (HS256). Separate from
 * {@link GoogleTokenVerifier}, which only verifies Google's ID token at login time.
 */
@Component
public class JwtService {

    private static final long EXPIRATION_DAYS = 30;

    private final SecretKeySpec key;

    public JwtService(@Value("${app.jwt.secret}") String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public String issueToken(Long userId, String email, Role role, Long gymId) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(email)
                    .claim("userId", userId)
                    .claim("role", role.name())
                    .claim("gymId", gymId)
                    .issueTime(new Date())
                    .expirationTime(Date.from(Instant.now().plus(EXPIRATION_DAYS, ChronoUnit.DAYS)))
                    .build();
            SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            signedJwt.sign(new MACSigner(key));
            return signedJwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign app JWT", e);
        }
    }

    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
