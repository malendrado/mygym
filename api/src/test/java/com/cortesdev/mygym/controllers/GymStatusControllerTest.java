package com.cortesdev.mygym.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymClosure;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymClosureRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.repositories.ReservationRepository.BookedStamp;
import com.cortesdev.mygym.controllers.GymStatusController.GymStatusResponse;
import java.time.Instant;
import java.time.LocalDate;
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
    @Mock private GymClosureRepository gymClosureRepository;
    @Mock private ReservationRepository reservationRepository;
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
        when(reservationRepository.bookedStamp(eq(GYM_ID), any())).thenReturn(booked(0, null));

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
        assertNull(status.closuresStamp());
        assertNull(status.reservationsStamp());
    }

    private static BookedStamp booked(long total, Long lastId) {
        return new BookedStamp() {
            @Override
            public long getTotal() {
                return total;
            }

            @Override
            public Long getLastId() {
                return lastId;
            }
        };
    }

    private String reservationsStampWith(long total, Long lastId) {
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(Gym.builder().id(GYM_ID).name("XaloGym").active(true).build()));
        when(reservationRepository.bookedStamp(eq(GYM_ID), any())).thenReturn(booked(total, lastId));
        return controller.status(jwt).reservationsStamp();
    }

    private static GymClosure closure(long id, int startDay, int endDay, Instant liftedAt) {
        return GymClosure.builder().id(id).gymId(GYM_ID)
                .startDate(LocalDate.of(2026, 10, startDay)).endDate(LocalDate.of(2026, 10, endDay)).liftedAt(liftedAt).build();
    }

    private String stampWith(List<GymClosure> closures) {
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(Gym.builder().id(GYM_ID).name("XaloGym").active(true).build()));
        when(gymClosureRepository.findByGymIdAndEndDateGreaterThanEqual(eq(GYM_ID), any())).thenReturn(closures);
        when(reservationRepository.bookedStamp(eq(GYM_ID), any())).thenReturn(booked(0, null));
        return controller.status(jwt).closuresStamp();
    }

    @Test
    void theClosuresStampChangesWhenAClosureIsCreatedEditedOrLifted() {
        String none = stampWith(List.of());
        String created = stampWith(List.of(closure(1, 20, 22, null)));
        String edited = stampWith(List.of(closure(1, 20, 25, null)));
        String lifted = stampWith(List.of(closure(1, 20, 22, Instant.parse("2026-10-21T15:00:00Z"))));

        assertEquals(4, java.util.Set.of(none, created, edited, lifted).size());
    }

    @Test
    void theClosuresStampIsStableForTheSameClosuresWhateverTheOrder() {
        String a = stampWith(List.of(closure(1, 20, 22, null), closure(2, 25, 26, null)));
        String b = stampWith(List.of(closure(2, 25, 26, null), closure(1, 20, 22, null)));

        assertEquals(a, b);
    }

    @Test
    void theClosuresStampRevealsNoClosureData() {
        String stamp = stampWith(List.of(closure(7, 20, 22, null)));

        assertFalse(stamp.contains("2026"));
        assertFalse(stamp.contains(":"));
    }

    @Test
    void theReservationsStampChangesWhenSomeoneBooksOrCancels() {
        String base = reservationsStampWith(5, 40L);
        String booked = reservationsStampWith(6, 41L);
        String cancelled = reservationsStampWith(4, 40L);
        String swapped = reservationsStampWith(5, 42L); // una cancela y otro reserva a la vez: el total no cambia pero si la mas nueva

        assertEquals(4, java.util.Set.of(base, booked, cancelled, swapped).size());
    }

    @Test
    void theReservationsStampIsStableWhileNothingChanges() {
        assertEquals(reservationsStampWith(5, 40L), reservationsStampWith(5, 40L));
    }

    @Test
    void theReservationsStampWorksWithNoReservationsAtAll() {
        assertEquals(reservationsStampWith(0, null), reservationsStampWith(0, null));
    }

    @Test
    void theReservationsStampRevealsNoReservationData() {
        String stamp = reservationsStampWith(5, 40L);

        assertFalse(stamp.contains(":"));
        assertFalse(stamp.contains("40"));
    }

    @Test
    void ifAStampQueryFailsTheStatusStillAnswers() {
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(Gym.builder().id(GYM_ID).name("XaloGym").active(true).build()));
        when(gymClosureRepository.findByGymIdAndEndDateGreaterThanEqual(eq(GYM_ID), any())).thenThrow(new RuntimeException("db"));
        when(reservationRepository.bookedStamp(eq(GYM_ID), any())).thenThrow(new RuntimeException("db"));

        GymStatusResponse status = controller.status(jwt);

        assertFalse(status.paused());
        assertNull(status.closuresStamp());
        assertNull(status.reservationsStamp());
    }
}
