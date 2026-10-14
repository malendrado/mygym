package com.cortesdev.mygym.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.repositories.GymRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Gimnasio en pausa: sus socios, admins y profesores pueden entrar pero la API solo les deja consultar el estado. */
@ExtendWith(MockitoExtension.class)
class GymPauseFilterTest {

    @Mock private GymRepository gymRepository;
    @Mock private FilterChain chain;

    private final ObjectMapper mapper = new ObjectMapper();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private GymPauseFilter filter() {
        return new GymPauseFilter(new GymPauseGuard(gymRepository), mapper);
    }

    private void login(String role, Long gymId) {
        Jwt.Builder builder = Jwt.withTokenValue("t")
                .header("alg", "none")
                .claim("userId", 1L)
                .claim("role", role)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (gymId != null) {
            builder.claim("gymId", gymId);
        }
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build()));
    }

    private MockHttpServletResponse run(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter().doFilter(request, response, chain);
        return response;
    }

    @Test
    void aPausedGymBlocksItsMembersAdminsAndTeachersWithAClearCode() throws Exception {
        when(gymRepository.existsByIdAndActiveTrue(5L)).thenReturn(false);
        for (String role : new String[] {"MEMBER", "GYM_ADMIN", "PROFESOR"}) {
            login(role, 5L);
            MockHttpServletResponse response = run("/api/me/reservations");

            assertEquals(403, response.getStatus(), role);
            Map<?, ?> body = mapper.readValue(response.getContentAsString(), Map.class);
            assertEquals("GYM_PAUSED", body.get("code"), role);
            assertEquals("Gimnasio en pausa", body.get("title"));
        }
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void anActiveGymPassesThrough() throws Exception {
        when(gymRepository.existsByIdAndActiveTrue(5L)).thenReturn(true);
        login("MEMBER", 5L);

        MockHttpServletResponse response = run("/api/me/reservations");

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void theStatusRouteStaysOpenSoTheAppCanExplainWhatIsHappening() throws Exception {
        login("MEMBER", 5L);

        run("/api/gym-status");

        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(gymRepository, never()).existsByIdAndActiveTrue(5L);
    }

    @Test
    void theSuperAdminHasNoGymAndIsNeverBlocked() throws Exception {
        login("SUPER_ADMIN", null);

        run("/api/gyms/5/config");

        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(gymRepository, never()).existsByIdAndActiveTrue(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void requestsWithoutASessionAreNotTouched() throws Exception {
        run("/api/public/gyms/xalogym");

        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(gymRepository, never()).existsByIdAndActiveTrue(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void theAnswerIsCachedAndReactivationIsNoticedRightAwayAfterInvalidating() throws Exception {
        GymPauseGuard guard = new GymPauseGuard(gymRepository);
        when(gymRepository.existsByIdAndActiveTrue(5L)).thenReturn(false, true);

        assertTrue(guard.isPaused(5L));
        assertTrue(guard.isPaused(5L)); // desde la memoria: una sola consulta
        verify(gymRepository, times(1)).existsByIdAndActiveTrue(5L);

        guard.invalidate(5L); // el super-admin lo reactiva
        assertEquals(false, guard.isPaused(5L));
        verify(gymRepository, times(2)).existsByIdAndActiveTrue(5L);
    }
}
