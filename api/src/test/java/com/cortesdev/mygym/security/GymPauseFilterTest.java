package com.cortesdev.mygym.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.GymSubscriptionRepository;
import java.util.Optional;
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
    @Mock private GymSubscriptionRepository subscriptionRepository;
    @Mock private FilterChain chain;

    private final ObjectMapper mapper = new ObjectMapper();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private GymPauseFilter filter() {
        return new GymPauseFilter(new GymPauseGuard(gymRepository, subscriptionRepository), mapper);
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
        GymPauseGuard guard = new GymPauseGuard(gymRepository, subscriptionRepository);
        when(gymRepository.existsByIdAndActiveTrue(5L)).thenReturn(false, true);

        assertTrue(guard.isPaused(5L));
        assertTrue(guard.isPaused(5L)); // desde la memoria: una sola consulta
        verify(gymRepository, times(1)).existsByIdAndActiveTrue(5L);

        guard.invalidate(5L); // el super-admin lo reactiva
        assertEquals(false, guard.isPaused(5L));
        verify(gymRepository, times(2)).existsByIdAndActiveTrue(5L);
    }

    @Test
    void aGymSuspendedForNonPaymentBlocksEveryoneExceptAnAdminPayingTheSubscription() throws Exception {
        when(gymRepository.existsByIdAndActiveTrue(5L)).thenReturn(true);
        when(subscriptionRepository.findBillingStatusByGymId(5L)).thenReturn(Optional.of("SUSPENDED"));

        login("MEMBER", 5L);
        MockHttpServletResponse member = run("/api/me/reservations");
        assertEquals(403, member.getStatus());
        assertEquals("BILLING", mapper.readValue(member.getContentAsString(), Map.class).get("reason"));

        login("GYM_ADMIN", 5L);
        assertEquals(403, run("/api/gym-admin/members").getStatus());
        assertEquals(200, run("/api/gym-admin/subscription").getStatus());
        assertEquals(200, run("/api/gym-admin/subscription/checkout").getStatus());

        login("PROFESOR", 5L);
        assertEquals(403, run("/api/gym-admin/subscription").getStatus());
    }

    @Test
    void aManualPauseNeverLetsTheAdminThroughTheSubscriptionRoutes() throws Exception {
        when(gymRepository.existsByIdAndActiveTrue(5L)).thenReturn(false);
        login("GYM_ADMIN", 5L);

        MockHttpServletResponse response = run("/api/gym-admin/subscription");

        assertEquals(403, response.getStatus());
        assertEquals("MANUAL", mapper.readValue(response.getContentAsString(), Map.class).get("reason"));
    }

    @Test
    void aGymThatWasNeverActivatedIsBlockedAndAGymWithoutASubscriptionRowIsNot() throws Exception {
        when(gymRepository.existsByIdAndActiveTrue(5L)).thenReturn(true);
        when(gymRepository.existsByIdAndActiveTrue(6L)).thenReturn(true);
        when(subscriptionRepository.findBillingStatusByGymId(5L)).thenReturn(Optional.of("NOT_STARTED"));
        when(subscriptionRepository.findBillingStatusByGymId(6L)).thenReturn(Optional.empty());

        login("MEMBER", 5L);
        assertEquals(403, run("/api/me/reservations").getStatus());
        login("MEMBER", 6L);
        assertEquals(200, run("/api/me/reservations").getStatus());
    }

    @Test
    void graceAndExemptGymsKeepWorking() throws Exception {
        when(gymRepository.existsByIdAndActiveTrue(5L)).thenReturn(true);
        when(subscriptionRepository.findBillingStatusByGymId(5L)).thenReturn(Optional.of("GRACE"));
        login("MEMBER", 5L);
        assertEquals(200, run("/api/me/reservations").getStatus());
    }
}
