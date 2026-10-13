package com.cortesdev.mygym.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cortesdev.mygym.models.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtServiceGuideTokenTest {

    private final JwtService jwtService = new JwtService("test-secret-test-secret-test-secret-123");

    @Test
    void guideTokenRoundTripsUserAndGym() {
        JwtService.GuideClaims claims = jwtService.verifyGuideToken(jwtService.issueGuideToken(7L, 3L));

        assertNotNull(claims);
        assertEquals(7L, claims.userId());
        assertEquals(3L, claims.gymId());
    }

    @Test
    void garbageAndEmptyTokensAreRejected() {
        assertNull(jwtService.verifyGuideToken("no-es-un-token"));
        assertNull(jwtService.verifyGuideToken(""));
    }

    @Test
    void sessionTokenIsNotAValidGuideToken() {
        String session = jwtService.issueToken(7L, "a@b.cl", Role.MEMBER, 3L);

        assertNull(jwtService.verifyGuideToken(session));
    }

    @Test
    void guideTokenCannotBeUsedAsSessionToken() {
        String guide = jwtService.issueGuideToken(7L, 3L);

        assertThrows(JwtException.class, () -> jwtService.jwtDecoder().decode(guide));
    }

    @Test
    void tokenIsShortEnoughToShowInAnEmail() {
        assertTrue(jwtService.issueGuideToken(123456L, 60L).length() < 80);
    }

    @Test
    void tamperedPayloadIsRejected() {
        String[] parts = jwtService.issueGuideToken(7L, 3L).split("\\.");
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("8.3.9999999999".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertNull(jwtService.verifyGuideToken(forgedPayload + "." + parts[1]));
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String foreign = new JwtService("otro-secreto-otro-secreto-otro-secreto-9").issueGuideToken(7L, 3L);

        assertNull(jwtService.verifyGuideToken(foreign));
    }
}
