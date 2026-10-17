package com.cortesdev.mygym.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** El límite se evadía rotando el X-Forwarded-For y dejaba sin cubrir varias rutas públicas (auditoría 2026-10-17). */
class AuthRateLimitFilterTest {

    private final AuthRateLimitFilter filter = new AuthRateLimitFilter(new ObjectMapper());

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private int post(String path, String forwardedFor) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        request.setRequestURI(path);
        request.setRemoteAddr("10.0.0.1");
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    void rotatingTheClientWrittenPartOfXForwardedForNoLongerEvadesTheLimit() throws Exception {
        // Railway agrega la IP real al final; todo lo anterior lo escribe el atacante.
        for (int i = 1; i <= 10; i++) {
            assertEquals(200, post("/api/public/auth/login", "9.9.9." + i + ", 203.0.113.7"));
        }
        assertEquals(429, post("/api/public/auth/login", "1.2.3.4, 203.0.113.7"));
    }

    @Test
    void differentRealClientsKeepSeparateCounters() throws Exception {
        for (int i = 1; i <= 10; i++) {
            post("/api/public/auth/login", "203.0.113.7");
        }
        assertEquals(429, post("/api/public/auth/login", "203.0.113.7"));
        assertEquals(200, post("/api/public/auth/login", "203.0.113.8"));
    }

    @Test
    void anEncodedSpellingOfTheRouteCountsAgainstTheSameLimit() throws Exception {
        // El servlet entrega la ruta decodificada; getRequestURI() trae el texto crudo (%6C = "l").
        for (int i = 1; i <= 10; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/public/auth/%6Cogin");
            request.setServletPath("/api/public/auth/login");
            request.setRequestURI("/api/public/auth/%6Cogin");
            request.addHeader("X-Forwarded-For", "203.0.113.9");
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        }
        assertEquals(429, post("/api/public/auth/login", "203.0.113.9"));
    }

    @Test
    void thePublicJoinRouteWithAGymSlugIsLimited() throws Exception {
        for (int i = 1; i <= 20; i++) {
            assertEquals(200, post("/api/public/gyms/xalogym/join", "203.0.113.10"));
        }
        assertEquals(429, post("/api/public/gyms/xalogym/join", "203.0.113.10"));
    }

    @Test
    void theOtherWriteHappyPublicRoutesAreLimitedToo() throws Exception {
        for (int i = 1; i <= 20; i++) {
            post("/api/public/tv/pairing", "203.0.113.11");
        }
        assertEquals(429, post("/api/public/tv/pairing", "203.0.113.11"));
        for (int i = 1; i <= 120; i++) {
            post("/api/public/analytics/visit", "203.0.113.11");
        }
        assertEquals(429, post("/api/public/analytics/visit", "203.0.113.11"));
    }

    @Test
    void checkinIsCountedPerMemberNotPerIpBecauseAWholeGymSharesTheWifi() throws Exception {
        login(1L);
        for (int i = 1; i <= 15; i++) {
            assertEquals(200, post("/api/me/checkin", "203.0.113.12"));
        }
        assertEquals(429, post("/api/me/checkin", "203.0.113.12"));

        login(2L); // otro socio desde la misma IP del gimnasio
        assertEquals(200, post("/api/me/checkin", "203.0.113.12"));
    }

    @Test
    void routesThatAreNotLimitedAreNeverCounted() throws Exception {
        for (int i = 1; i <= 50; i++) {
            assertEquals(200, post("/api/me/reservations", "203.0.113.13"));
        }
    }

    private void login(long userId) {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .claim("userId", userId)
                .claim("role", "MEMBER")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
