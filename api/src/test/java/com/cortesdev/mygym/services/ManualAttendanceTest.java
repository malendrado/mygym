package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.CheckinMethod;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.services.exception.InvalidAttendanceException;
import com.cortesdev.mygym.services.exception.ReservationNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Marcado de asistencia a mano (Mockito, sin DB): ventana, gym ajeno, idempotencia y quién lo hizo. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ManualAttendanceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Santiago");
    private static final Long GYM_ID = 7L;
    private static final Long OTHER_GYM_ID = 8L;
    private static final Long BLOCK_ID = 11L;
    private static final Long RESERVATION_ID = 31L;
    private static final Long ACTOR_ID = 99L;

    @Mock private GymBlockRepository gymBlockRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private FixedWeekRule fixedWeekRule;
    @InjectMocks private ReservationService reservationService;

    private Reservation reservation(LocalDate classDate, Instant checkedInAt) {
        Reservation r = Reservation.builder()
                .id(RESERVATION_ID)
                .gymBlockId(BLOCK_ID)
                .memberId(21L)
                .classDate(classDate)
                .status(ReservationStatus.BOOKED)
                .checkedInAt(checkedInAt)
                .checkinMethod(checkedInAt == null ? null : CheckinMethod.QR)
                .build();
        when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(r));
        return r;
    }

    private void block(Long gymId) {
        when(gymBlockRepository.findById(BLOCK_ID))
                .thenReturn(Optional.of(GymBlock.builder()
                        .id(BLOCK_ID)
                        .gymId(gymId)
                        .label("GAP")
                        .startTime(LocalTime.of(19, 0))
                        .endTime(LocalTime.of(20, 0))
                        .active(true)
                        .build()));
    }

    private static LocalDate daysAgo(long days) {
        return ZonedDateTime.now(ZONE).toLocalDate().minusDays(days);
    }

    @Test
    void marcaAManoGuardaMetodoYQuien() {
        Reservation r = reservation(daysAgo(1), null);
        block(GYM_ID);

        var mark = reservationService.setAttendance(GYM_ID, RESERVATION_ID, true, ACTOR_ID);

        assertThat(r.getCheckedInAt()).isNotNull();
        assertThat(r.getCheckinMethod()).isEqualTo(CheckinMethod.MANUAL);
        assertThat(r.getCheckedInByUserId()).isEqualTo(ACTOR_ID);
        assertThat(mark.checkinMethod()).isEqualTo(CheckinMethod.MANUAL);
        verify(reservationRepository).save(r);
    }

    @Test
    void marcarDosVecesNoCambiaLaHoraOriginal() {
        Instant original = Instant.parse("2026-10-10T22:04:00Z");
        Reservation r = reservation(daysAgo(1), original);
        block(GYM_ID);

        reservationService.setAttendance(GYM_ID, RESERVATION_ID, true, ACTOR_ID);

        assertThat(r.getCheckedInAt()).isEqualTo(original);
        assertThat(r.getCheckinMethod()).isEqualTo(CheckinMethod.QR);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void desmarcaTambienUnCheckInHechoPorQrYDejaQuienLoQuito() {
        Reservation r = reservation(daysAgo(1), Instant.parse("2026-10-10T22:04:00Z"));
        block(GYM_ID);

        var mark = reservationService.setAttendance(GYM_ID, RESERVATION_ID, false, ACTOR_ID);

        assertThat(r.getCheckedInAt()).isNull();
        assertThat(r.getCheckinMethod()).isNull();
        assertThat(r.getCheckedInByUserId()).isEqualTo(ACTOR_ID);
        assertThat(mark.checkedInAt()).isNull();
    }

    @Test
    void desmarcarAlguienSinMarcaNoHaceNada() {
        reservation(daysAgo(1), null);
        block(GYM_ID);

        reservationService.setAttendance(GYM_ID, RESERVATION_ID, false, ACTOR_ID);

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void rechazaUnaClaseDeMananaPorqueNoEmpezo() {
        reservation(daysAgo(-1), null);
        block(GYM_ID);

        assertThatThrownBy(() -> reservationService.setAttendance(GYM_ID, RESERVATION_ID, true, ACTOR_ID))
                .isInstanceOf(InvalidAttendanceException.class)
                .hasMessageContaining("hasta 7 días");
    }

    @Test
    void rechazaUnaClaseDeHaceMasDeSieteDias() {
        reservation(daysAgo(10), null);
        block(GYM_ID);

        assertThatThrownBy(() -> reservationService.setAttendance(GYM_ID, RESERVATION_ID, true, ACTOR_ID))
                .isInstanceOf(InvalidAttendanceException.class);
    }

    @Test
    void aceptaUnaClaseDeHaceSeisDias() {
        Reservation r = reservation(daysAgo(6), null);
        block(GYM_ID);

        reservationService.setAttendance(GYM_ID, RESERVATION_ID, true, ACTOR_ID);

        assertThat(r.getCheckedInAt()).isNotNull();
    }

    @Test
    void reservaDeOtroGymEsNotFoundSinRevelarQueExiste() {
        reservation(daysAgo(1), null);
        block(OTHER_GYM_ID);

        assertThatThrownBy(() -> reservationService.setAttendance(GYM_ID, RESERVATION_ID, true, ACTOR_ID))
                .isInstanceOf(ReservationNotFoundException.class);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void reservaCanceladaEsNotFound() {
        Reservation r = reservation(daysAgo(1), null);
        r.setStatus(ReservationStatus.CANCELLED);
        block(GYM_ID);

        assertThatThrownBy(() -> reservationService.setAttendance(GYM_ID, RESERVATION_ID, true, ACTOR_ID))
                .isInstanceOf(ReservationNotFoundException.class);
    }

    @Test
    void ventanaDeEdicionAbreDiezMinutosAntesYCierraASieteDias() {
        LocalDate today = ZonedDateTime.now(ZONE).toLocalDate();
        // Clase de hoy que empieza en 5 min: ya se puede marcar (ventana de 10 min).
        LocalTime soon = ZonedDateTime.now(ZONE).plusMinutes(5).toLocalTime();
        if (soon.isAfter(LocalTime.of(0, 10))) { // evita el cruce de medianoche
            assertThat(ReservationService.isAttendanceEditable(today, soon, soon.plusMinutes(30)))
                    .isTrue();
        }
        // Una que empieza en 2 horas todavía no.
        LocalTime later = ZonedDateTime.now(ZONE).plusHours(2).toLocalTime();
        if (later.isAfter(ZonedDateTime.now(ZONE).toLocalTime())) {
            assertThat(ReservationService.isAttendanceEditable(today, later, later.plusMinutes(30)))
                    .isFalse();
        }
    }
}
