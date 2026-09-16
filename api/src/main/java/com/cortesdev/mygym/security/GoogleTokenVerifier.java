package com.cortesdev.mygym.security;

import com.cortesdev.mygym.services.exception.InvalidGoogleTokenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * Verifies a Google Identity Services ID token at login time. The decoder does OIDC discovery
 * against Google (network call) on first use only — built lazily so app startup and unrelated
 * tests never depend on reaching accounts.google.com.
 */
@Component
public class GoogleTokenVerifier {

    private final String googleClientId;
    private volatile JwtDecoder delegate;

    public GoogleTokenVerifier(@Value("${app.google.client-id}") String googleClientId) {
        this.googleClientId = googleClientId;
    }

    public GoogleIdentity verify(String idToken) {
        Jwt jwt;
        try {
            jwt = decoder().decode(idToken);
        } catch (JwtException e) {
            throw new InvalidGoogleTokenException("No pudimos verificar el token de Google");
        }
        if (!jwt.getAudience().contains(googleClientId)) {
            throw new InvalidGoogleTokenException("El token de Google no corresponde a esta aplicación");
        }
        return new GoogleIdentity(
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name"),
                jwt.getClaimAsString("picture"));
    }

    private JwtDecoder decoder() {
        JwtDecoder local = delegate;
        if (local == null) {
            synchronized (this) {
                local = delegate;
                if (local == null) {
                    local = JwtDecoders.fromIssuerLocation("https://accounts.google.com");
                    delegate = local;
                }
            }
        }
        return local;
    }

    public record GoogleIdentity(String googleSub, String email, String name, String pictureUrl) {}
}
