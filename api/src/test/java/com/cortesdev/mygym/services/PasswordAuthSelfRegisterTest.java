package com.cortesdev.mygym.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.AuthTokenRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.security.JwtService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** "Unirme con mi email" de la página de un gimnasio (Mockito, sin DB): quien ya tiene cuenta ahí recibe el correo
 *  de crear/recuperar contraseña; en cualquier otro caso no se manda nada y el resultado externo es el mismo. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordAuthSelfRegisterTest {

    @Mock private AppUserRepository appUserRepository;
    @Mock private AuthTokenRepository authTokenRepository;
    @Mock private GymRepository gymRepository;
    @Mock private GymCapacityService gymCapacityService;
    @Mock private JwtService jwtService;
    @Mock private MemberLifecycleEmailService emailService;

    private PasswordAuthService service;
    private final Gym gym = Gym.builder().id(60L).slug("xalogym").name("XaloGym").active(true).build();

    @BeforeEach
    void setUp() {
        service = new PasswordAuthService(
                appUserRepository, authTokenRepository, gymRepository, gymCapacityService, jwtService, emailService);
        when(gymRepository.findBySlug("xalogym")).thenReturn(Optional.of(gym));
        when(gymRepository.findById(60L)).thenReturn(Optional.of(gym));
    }

    private AppUser user(Long gymId, boolean active) {
        return AppUser.builder().id(5L).email("ana@x.cl").name("Ana").gymId(gymId).active(active).build();
    }

    @Test
    void cuentaExistenteDelMismoGimnasioRecibeElCorreoDeRecuperar() {
        AppUser ana = user(60L, true);
        when(appUserRepository.findByEmail("ana@x.cl")).thenReturn(Optional.of(ana));

        service.requestSelfRegistration("Ana", "Ana@X.cl", "xalogym");

        verify(emailService).sendPasswordReset(eq(gym), eq(ana), any());
        verify(emailService, never()).sendSelfRegisterActivation(any(), any(), any(), any());
    }

    @Test
    void cuentaDeOtroGimnasioNoRecibeNada() {
        when(appUserRepository.findByEmail("ana@x.cl")).thenReturn(Optional.of(user(99L, true)));

        service.requestSelfRegistration("Ana", "ana@x.cl", "xalogym");

        verify(emailService, never()).sendPasswordReset(any(), any(), any());
        verify(emailService, never()).sendSelfRegisterActivation(any(), any(), any(), any());
    }

    @Test
    void cuentaInactivaNoRecibeNada() {
        when(appUserRepository.findByEmail("ana@x.cl")).thenReturn(Optional.of(user(60L, false)));

        service.requestSelfRegistration("Ana", "ana@x.cl", "xalogym");

        verify(emailService, never()).sendPasswordReset(any(), any(), any());
    }

    @Test
    void emailNuevoRecibeElCorreoDeActivacionDeSiempre() {
        when(appUserRepository.findByEmail("nuevo@x.cl")).thenReturn(Optional.empty());

        service.requestSelfRegistration("Nuevo", "nuevo@x.cl", "xalogym");

        verify(emailService).sendSelfRegisterActivation(eq(gym), eq("Nuevo"), eq("nuevo@x.cl"), any());
        verify(emailService, never()).sendPasswordReset(any(), any(), any());
    }

    @Test
    void sinCupoNoSeEnviaElCorreoDeActivacionYSeLeExplicaALaPersona() {
        when(appUserRepository.findByEmail("nueva@x.cl")).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new com.cortesdev.mygym.services.exception.GymLimitReachedException(
                        com.cortesdev.mygym.services.exception.GymLimitReachedException.Kind.MEMBERS, "sin cupos"))
                .when(gymCapacityService).assertSelfRegistrationAllowed(gym);

        org.junit.jupiter.api.Assertions.assertThrows(
                com.cortesdev.mygym.services.exception.GymLimitReachedException.class,
                () -> service.requestSelfRegistration("Nueva", "nueva@x.cl", "xalogym"));

        verify(emailService, never()).sendSelfRegisterActivation(any(), any(), any(), any());
        verify(authTokenRepository, never()).save(any());
    }
}
