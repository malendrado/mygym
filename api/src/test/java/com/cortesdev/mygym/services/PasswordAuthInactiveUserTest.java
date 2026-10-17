package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.AuthToken;
import com.cortesdev.mygym.models.AuthTokenPurpose;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.AuthTokenRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.security.JwtService;
import com.cortesdev.mygym.services.exception.InvalidOrExpiredTokenException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Una invitación (48 h) o un reseteo (1 h) emitidos antes de desactivar la cuenta no deben devolver una sesión
 * (auditoría 2026-10-17): login() ya rechazaba a los inactivos, activate() no.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordAuthInactiveUserTest {

    @Mock private AppUserRepository appUserRepository;
    @Mock private AuthTokenRepository authTokenRepository;
    @Mock private GymRepository gymRepository;
    @Mock private GymCapacityService gymCapacityService;
    @Mock private JwtService jwtService;
    @Mock private MemberLifecycleEmailService emailService;

    private PasswordAuthService service;

    @BeforeEach
    void setUp() {
        service = new PasswordAuthService(
                appUserRepository, authTokenRepository, gymRepository, gymCapacityService, jwtService, emailService);
    }

    private void tokenFor(AuthTokenPurpose purpose, AppUser user) {
        AuthToken token = AuthToken.builder()
                .tokenHash("h")
                .purpose(purpose)
                .appUserId(user.getId())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(authTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(appUserRepository.findById(user.getId())).thenReturn(Optional.of(user));
    }

    private AppUser user(boolean active) {
        return AppUser.builder().id(5L).email("ana@x.cl").name("Ana").role(Role.GYM_ADMIN).gymId(60L).active(active).build();
    }

    @Test
    void anInviteLinkForADeactivatedAccountDoesNotIssueASession() {
        AppUser ana = user(false);
        tokenFor(AuthTokenPurpose.INVITE, ana);

        assertThatThrownBy(() -> service.activate("raw", "una-clave-larga"))
                .isInstanceOf(InvalidOrExpiredTokenException.class);

        verify(jwtService, never()).issueToken(any(), any(), any(), any());
        verify(appUserRepository, never()).save(any());
        assertThat(ana.getPasswordHash()).isNull();
    }

    @Test
    void aResetLinkForADeactivatedAccountDoesNotIssueASession() {
        tokenFor(AuthTokenPurpose.PASSWORD_RESET, user(false));

        assertThatThrownBy(() -> service.activate("raw", "una-clave-larga"))
                .isInstanceOf(InvalidOrExpiredTokenException.class);

        verify(jwtService, never()).issueToken(any(), any(), any(), any());
    }

    @Test
    void theTokenInfoOfADeactivatedAccountLooksLikeAnyExpiredLink() {
        tokenFor(AuthTokenPurpose.INVITE, user(false));

        assertThatThrownBy(() -> service.tokenInfo("raw")).isInstanceOf(InvalidOrExpiredTokenException.class);
    }

    @Test
    void anActiveAccountStillActivatesNormally() {
        AppUser ana = user(true);
        tokenFor(AuthTokenPurpose.INVITE, ana);
        when(jwtService.issueToken(any(), any(), any(), any())).thenReturn("jwt");

        service.activate("raw", "una-clave-larga");

        verify(jwtService).issueToken(5L, "ana@x.cl", Role.GYM_ADMIN, 60L);
        assertThat(ana.getPasswordHash()).isNotNull();
    }
}
