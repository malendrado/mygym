package com.cortesdev.mygym.security;

import com.cortesdev.mygym.models.Role;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Mac;
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
    private static final long GUIDE_EXPIRATION_DAYS = 30;
    private static final int GUIDE_SIGNATURE_BYTES = 16;
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_DECODER = Base64.getUrlDecoder();

    private final SecretKeySpec key;
    // Clave aparte para los enlaces de guía: el decoder de sesión no puede verificarlos, así que un
    // token de guía nunca sirve como Bearer contra la API.
    private final SecretKeySpec guideKey;

    public JwtService(@Value("${app.jwt.secret}") String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.guideKey = new SecretKeySpec((secret + ":guide").getBytes(StandardCharsets.UTF_8), "HmacSHA256");
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

    /** Claims de un enlace de guía válido — solo identifica al socio y su gym, nunca da sesión. */
    public record GuideClaims(Long userId, Long gymId) {}

    /**
     * Token de la "Guía del socio" que va en el correo de bienvenida: personal, firmado y con
     * vencimiento. A propósito NO es un JWT: el correo muestra el enlace completo como texto
     * ("copia este enlace") y un JWT de ~230 caracteres se ve ilegible. Formato compacto
     * `base64url("userId.gymId.expEpoch") + "." + base64url(HMAC-SHA256 truncado a 16 bytes)`
     * (~55 caracteres), firmado con una clave distinta a la de sesión (ver guideKey): jamás
     * autentica nada, solo identifica al socio para mostrarle la guía.
     */
    public String issueGuideToken(Long userId, Long gymId) {
        long expiresAt = Instant.now().plus(GUIDE_EXPIRATION_DAYS, ChronoUnit.DAYS).getEpochSecond();
        String payload = userId + "." + gymId + "." + expiresAt;
        return B64.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + B64.encodeToString(guideSignature(payload));
    }

    /** Devuelve los claims si el token es de guía, está bien firmado y no venció; si no, null. */
    public GuideClaims verifyGuideToken(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2) {
                return null;
            }
            String payload = new String(B64_DECODER.decode(parts[0]), StandardCharsets.UTF_8);
            // Comparación en tiempo constante: no filtra cuántos bytes de la firma acertó.
            if (!MessageDigest.isEqual(guideSignature(payload), B64_DECODER.decode(parts[1]))) {
                return null;
            }
            String[] fields = payload.split("\\.");
            if (fields.length != 3 || Long.parseLong(fields[2]) < Instant.now().getEpochSecond()) {
                return null;
            }
            return new GuideClaims(Long.parseLong(fields[0]), Long.parseLong(fields[1]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private byte[] guideSignature(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(guideKey);
            return Arrays.copyOf(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)), GUIDE_SIGNATURE_BYTES);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not sign guide token", e);
        }
    }

    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
