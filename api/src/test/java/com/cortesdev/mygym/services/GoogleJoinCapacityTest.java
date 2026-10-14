package com.cortesdev.mygym.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.LoginResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.security.GoogleTokenVerifier;
import com.cortesdev.mygym.security.GoogleTokenVerifier.GoogleIdentity;
import com.cortesdev.mygym.security.JwtService;
import com.cortesdev.mygym.services.exception.GymLimitReachedException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Alta publica con Google (mygym.cl/j/xalogym) cuando el gimnasio ya no tiene cupos. */
@ExtendWith(MockitoExtension.class)
class GoogleJoinCapacityTest {

    @Mock private AppUserRepository appUserRepository;
    @Mock private GymRepository gymRepository;
    @Mock private GymCapacityService gymCapacityService;
    @Mock private GoogleTokenVerifier googleTokenVerifier;
    @Mock private JwtService jwtService;
    @Mock private MemberLifecycleEmailService memberLifecycleEmailService;
    @Mock private DemoAccessService demoAccessService;
    @InjectMocks private AuthService authService;

    private final Gym gym = Gym.builder().id(60L).slug("xalogym").name("XaloGym").maxUsers(10).active(true).build();
    private final GoogleIdentity identity = new GoogleIdentity("sub-1", "nueva@gmail.com", "Nueva Socia", null);

    @Test
    void aNewPersonCannotRegisterWhenTheGymIsFull_noAccountIsCreated() {
        when(googleTokenVerifier.verify("tok")).thenReturn(identity);
        when(gymRepository.findBySlug("xalogym")).thenReturn(Optional.of(gym));
        when(appUserRepository.findByEmail("nueva@gmail.com")).thenReturn(Optional.empty());
        doThrow(new GymLimitReachedException(GymLimitReachedException.Kind.MEMBERS, "Por ahora XaloGym no tiene cupos disponibles."))
                .when(gymCapacityService).assertSelfRegistrationAllowed(gym);

        GymLimitReachedException ex = assertThrows(GymLimitReachedException.class, () -> authService.joinGymWithGoogle("tok", "xalogym"));

        assertTrue(ex.getMessage().contains("no tiene cupos disponibles"));
        verify(appUserRepository, never()).save(any(AppUser.class));
        verify(memberLifecycleEmailService, never()).sendMemberWelcome(any(), any());
        verify(jwtService, never()).issueToken(any(), any(), any(), any());
    }

    @Test
    void someoneWhoAlreadyHasAnAccountStillEntersEvenWhenTheGymIsFull() {
        AppUser existing = AppUser.builder().id(7L).email("nueva@gmail.com").name("Nueva").role(Role.MEMBER).gymId(60L).active(true).build();
        when(googleTokenVerifier.verify("tok")).thenReturn(identity);
        when(gymRepository.findBySlug("xalogym")).thenReturn(Optional.of(gym));
        when(appUserRepository.findByEmail("nueva@gmail.com")).thenReturn(Optional.of(existing));
        when(jwtService.issueToken(7L, "nueva@gmail.com", Role.MEMBER, 60L)).thenReturn("jwt");

        LoginResponse response = authService.joinGymWithGoogle("tok", "xalogym");

        assertEquals("jwt", response.token());
        verify(gymCapacityService, never()).assertSelfRegistrationAllowed(any()); // el cupo solo importa para cuentas NUEVAS
    }
}
