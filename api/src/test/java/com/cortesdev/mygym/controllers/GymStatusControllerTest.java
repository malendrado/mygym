package com.cortesdev.mygym.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.controllers.GymStatusController.GymStatusResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GymStatusControllerTest {

    private static final Long GYM_ID = 5L;

    @Mock private GymRepository gymRepository;
    @Mock private AppUserRepository appUserRepository;
    @InjectMocks private GymStatusController controller;

    private final Jwt jwt = Jwt.withTokenValue("t")
            .header("alg", "none")
            .subject("socio@x.cl")
            .claim("userId", 9L)
            .claim("role", "MEMBER")
            .claim("gymId", GYM_ID)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();

    @BeforeEach
    void config() {
        ReflectionTestUtils.setField(controller, "mygymWhatsapp", "56964641042");
        ReflectionTestUtils.setField(controller, "mygymEmail", "contacto@mygym.cl");
    }

    @Test
    void anActiveGymRevealsNoContactDataAtAll() {
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(Gym.builder().id(GYM_ID).name("XaloGym").active(true).build()));

        GymStatusResponse status = controller.status(jwt);

        assertFalse(status.paused());
        assertNull(status.gymContact());
        assertNull(status.mygymContact());
    }

    @Test
    void aPausedGymGivesMembersTheGymsContactAndTheTeamMygymsContact() {
        when(gymRepository.findById(GYM_ID))
                .thenReturn(Optional.of(Gym.builder()
                        .id(GYM_ID).name("XaloGym").active(false)
                        .whatsappNumber("56911112222").instagramUrl("https://instagram.com/xalogym").build()));
        // El correo de contacto es el del PRIMER administrador creado (el menor id), no el de cualquiera.
        when(appUserRepository.findByGymIdAndRole(GYM_ID, Role.GYM_ADMIN))
                .thenReturn(List.of(
                        AppUser.builder().id(20L).email("segundo@x.cl").build(),
                        AppUser.builder().id(11L).email("dueno@xalogym.cl").build()));

        GymStatusResponse status = controller.status(jwt);

        assertTrue(status.paused());
        assertEquals("XaloGym", status.gymName());
        assertEquals("56911112222", status.gymContact().whatsappNumber());
        assertEquals("dueno@xalogym.cl", status.gymContact().email());
        assertEquals("https://instagram.com/xalogym", status.gymContact().instagramUrl());
        assertEquals("56964641042", status.mygymContact().whatsappNumber());
        assertEquals("contacto@mygym.cl", status.mygymContact().email());
    }
}
