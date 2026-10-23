package com.cortesdev.mygym.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.AppUserRepository.SessionView;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
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

/**
 * El JWT dura 30 días y trae rol y gymId firmados: quitar o desactivar a alguien tiene que cortarle el
 * acceso aunque su token siga vigente (auditoría 2026-10-17).
 */
@ExtendWith(MockitoExtension.class)
class UserSessionFilterTest {

    private static final long USER_ID = 7L;
    private static final long GYM_ID = 5L;

    @Mock private AppUserRepository appUserRepository;
    @Mock private FilterChain chain;
    @Mock private LastSeenTracker lastSeenTracker;

    private final ObjectMapper mapper = new ObjectMapper();

    @AfterEach
    void clear() {
        SecurityContextHolder.getContext().setAuthentication(null);
        SecurityContextHolder.clearContext();
    }

    private UserSessionFilter filter() {
        return new UserSessionFilter(new UserSessionGuard(appUserRepository), lastSeenTracker, mapper);
    }

    private void login(String role, Long gymId) {
        Jwt.Builder builder = Jwt.withTokenValue("t")
                .header("alg", "none")
                .claim("userId", USER_ID)
                .claim("role", role)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (gymId != null) {
            builder.claim("gymId", gymId);
        }
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build()));
    }

    private SessionView row(Role role, Long gymId, boolean active) {
        SessionView view = org.mockito.Mockito.mock(SessionView.class);
        when(view.getRole()).thenReturn(role);
        when(view.getGymId()).thenReturn(gymId);
        when(view.getActive()).thenReturn(active);
        return view;
    }

    private MockHttpServletResponse run() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/gym-admin/gym");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter().doFilter(request, response, chain);
        return response;
    }

    private void assertRevoked(MockHttpServletResponse response) throws Exception {
        assertEquals(401, response.getStatus());
        Map<?, ?> body = mapper.readValue(response.getContentAsString(), Map.class);
        assertEquals("SESSION_REVOKED", body.get("code"));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void aTokenIssuedBeforeThePasswordChangeIsRevokedButOneIssuedAfterSurvives() {
        UserSessionGuard guard = new UserSessionGuard(appUserRepository);
        SessionView view = row(Role.MEMBER, GYM_ID, true);
        Instant changedAt = Instant.parse("2026-10-17T15:00:00Z");
        when(view.getSessionsValidAfter()).thenReturn(changedAt);
        when(appUserRepository.findSessionViewById(USER_ID)).thenReturn(Optional.of(view));

        assertEquals(false, guard.isValid(USER_ID, "MEMBER", GYM_ID, changedAt.minusSeconds(1)));
        assertEquals(true, guard.isValid(USER_ID, "MEMBER", GYM_ID, changedAt)); // emitido en el mismo segundo: vale
        assertEquals(true, guard.isValid(USER_ID, "MEMBER", GYM_ID, changedAt.plusSeconds(60)));
        // Sin fecha de emisión conocida (o sin invalidación registrada) no se compara nada.
        assertEquals(true, guard.isValid(USER_ID, "MEMBER", GYM_ID));
    }

    @Test
    void anActiveUserWithTheSameRoleAndGymPassesThrough() throws Exception {
        SessionView view = row(Role.GYM_ADMIN, GYM_ID, true);
        when(appUserRepository.findSessionViewById(USER_ID)).thenReturn(Optional.of(view));
        login("GYM_ADMIN", GYM_ID);

        MockHttpServletResponse response = run();

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(any(), any());
        // Una sesión válida cuenta como actividad: el tracker decide si toca la base (una vez por hora).
        verify(lastSeenTracker).touch(USER_ID, "GYM_ADMIN", "/api/gym-admin/gym");
    }

    @Test
    void aRevokedSessionIsNeverRecordedAsActivity() throws Exception {
        when(appUserRepository.findSessionViewById(USER_ID)).thenReturn(Optional.empty());
        login("GYM_ADMIN", GYM_ID);

        assertRevoked(run());

        verify(lastSeenTracker, never()).touch(any(), any(), any());
    }

    @Test
    void aRemovedUserLosesAccessEvenWithAValidToken() throws Exception {
        when(appUserRepository.findSessionViewById(USER_ID)).thenReturn(Optional.empty());
        login("GYM_ADMIN", GYM_ID);

        assertRevoked(run());
    }

    @Test
    void aDeactivatedUserLosesAccess() throws Exception {
        SessionView view = row(Role.PROFESOR, GYM_ID, false);
        when(appUserRepository.findSessionViewById(USER_ID)).thenReturn(Optional.of(view));
        login("PROFESOR", GYM_ID);

        assertRevoked(run());
    }

    @Test
    void aTokenWhoseRoleNoLongerMatchesTheUserIsRefused() throws Exception {
        // Fue GYM_ADMIN cuando se emitió el token; hoy la fila dice PROFESOR.
        SessionView view = row(Role.PROFESOR, GYM_ID, true);
        when(appUserRepository.findSessionViewById(USER_ID)).thenReturn(Optional.of(view));
        login("GYM_ADMIN", GYM_ID);

        assertRevoked(run());
    }

    @Test
    void aTokenWhoseGymNoLongerMatchesTheUserIsRefused() throws Exception {
        SessionView view = row(Role.MEMBER, 99L, true);
        when(appUserRepository.findSessionViewById(USER_ID)).thenReturn(Optional.of(view));
        login("MEMBER", GYM_ID);

        assertRevoked(run());
    }

    @Test
    void theSuperAdminHasNoGymAndStillPassesWhenActive() throws Exception {
        SessionView view = row(Role.SUPER_ADMIN, null, true);
        when(appUserRepository.findSessionViewById(USER_ID)).thenReturn(Optional.of(view));
        login("SUPER_ADMIN", null);

        MockHttpServletResponse response = run();

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void aStaleTokenSentToAPublicRouteDoesNotBlockLoginOrThePublicPages() throws Exception {
        // El front adjunta el token guardado a TODA llamada a la API: alguien a quien ya le quitaron el acceso intenta
        // entrar de nuevo, o abre la página pública de su gimnasio, con ese token viejo en el navegador.
        login("MEMBER", GYM_ID);
        for (String path : new String[] {"/api/auth/google", "/api/public/auth/login", "/api/public/gyms/xalogym"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
            request.setServletPath(path);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter().doFilter(request, response, chain);
            assertEquals(200, response.getStatus(), path);
        }
        verify(chain, times(3)).doFilter(any(), any());
        verify(appUserRepository, never()).findSessionViewById(anyLong());
    }

    @Test
    void requestsWithoutASessionAreNotTouched() throws Exception {
        run();

        verify(chain).doFilter(any(), any());
        verify(appUserRepository, never()).findSessionViewById(anyLong());
    }

    @Test
    void theAnswerIsCachedAndInvalidatingForcesAFreshLookup() {
        UserSessionGuard guard = new UserSessionGuard(appUserRepository);
        SessionView view = row(Role.GYM_ADMIN, GYM_ID, true);
        when(appUserRepository.findSessionViewById(USER_ID)).thenReturn(Optional.of(view)).thenReturn(Optional.empty());

        assertEquals(true, guard.isValid(USER_ID, "GYM_ADMIN", GYM_ID));
        assertEquals(true, guard.isValid(USER_ID, "GYM_ADMIN", GYM_ID)); // desde la memoria: una sola consulta
        verify(appUserRepository, times(1)).findSessionViewById(USER_ID);

        guard.invalidate(USER_ID); // el admin lo quita
        assertEquals(false, guard.isValid(USER_ID, "GYM_ADMIN", GYM_ID));
        verify(appUserRepository, times(2)).findSessionViewById(USER_ID);
    }
}
