package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.AuthTokenRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.security.JwtService;
import com.cortesdev.mygym.services.exception.InvalidCredentialsException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * login() calcula bcrypt siempre, exista o no la cuenta: un correo inexistente ya no responde al instante (esa
 * diferencia de tiempo delataba qué correos tienen cuenta). Los resultados visibles no cambian.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordAuthLoginTest {

    @Mock private AppUserRepository appUserRepository;
    @Mock private AuthTokenRepository authTokenRepository;
    @Mock private GymRepository gymRepository;
    @Mock private GymCapacityService gymCapacityService;
    @Mock private JwtService jwtService;
    @Mock private MemberLifecycleEmailService emailService;

    private PasswordAuthService service;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        service = new PasswordAuthService(
                appUserRepository, authTokenRepository, gymRepository, gymCapacityService, jwtService, emailService);
        when(jwtService.issueToken(any(), any(), any(), any())).thenReturn("jwt");
    }

    private AppUser user(String passwordHash, boolean active) {
        return AppUser.builder().id(5L).email("ana@x.cl").name("Ana").role(Role.MEMBER).gymId(60L)
                .passwordHash(passwordHash).active(active).build();
    }

    @Test
    void rightPasswordOfAnActiveAccountEntersAsBefore() {
        when(appUserRepository.findByEmail("ana@x.cl")).thenReturn(Optional.of(user(encoder.encode("secreta-123"), true)));

        assertThat(service.login("Ana@X.cl", "secreta-123").token()).isEqualTo("jwt");
    }

    @Test
    void everyKindOfFailureLooksTheSame() {
        when(appUserRepository.findByEmail("ana@x.cl")).thenReturn(Optional.of(user(encoder.encode("secreta-123"), true)));
        assertThatThrownBy(() -> service.login("ana@x.cl", "otra")).isInstanceOf(InvalidCredentialsException.class);

        when(appUserRepository.findByEmail("nadie@x.cl")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login("nadie@x.cl", "secreta-123")).isInstanceOf(InvalidCredentialsException.class);

        // Cuenta que solo entra con Google (nunca tuvo clave).
        when(appUserRepository.findByEmail("g@x.cl")).thenReturn(Optional.of(user(null, true)));
        assertThatThrownBy(() -> service.login("g@x.cl", "lo-que-sea")).isInstanceOf(InvalidCredentialsException.class);

        // Clave correcta pero cuenta desactivada.
        when(appUserRepository.findByEmail("off@x.cl")).thenReturn(Optional.of(user(encoder.encode("secreta-123"), false)));
        assertThatThrownBy(() -> service.login("off@x.cl", "secreta-123")).isInstanceOf(InvalidCredentialsException.class);
    }
}
