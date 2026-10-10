package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.dto.ReservationCreateRequest;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.services.exception.AttendeesHiddenException;
import com.cortesdev.mygym.services.exception.BookingWindowClosedException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unidades puras (Mockito, sin DB): límite de reserva y límite de cancelación son DOS datos
 *  distintos del gym (V36), en minutos; y el flag de asistentes solo afecta la vista del socio. */
@ExtendWith(MockitoExtension.class)
class ReservationServiceWindowsTest {

    private static final ZoneId ZONE = ZoneId.of("America/Santiago");
    private static final Long GYM_ID = 7L;
    private static final Long BLOCK_ID = 11L;
    private static final Long MEMBER_ID = 21L;
    private static final Long RESERVATION_ID = 31L;

    @Mock private GymRepository gymRepository;
    @Mock private GymBlockRepository gymBlockRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private GymClosureService gymClosureService;
    @Mock private WaitlistService waitlistService;
    @Mock private com.cortesdev.mygym.repositories.AppUserRepository appUserRepository;
    @InjectMocks private ReservationService reservationService;

    /** Bloque que empieza dentro de `minutesFromNow` minutos (redondeado hacia arriba al minuto). */
    private GymBlock blockStartingIn(long minutesFromNow) {
        ZonedDateTime start = ZonedDateTime.now(ZONE).plusMinutes(minutesFromNow).plusSeconds(30);
        return GymBlock.builder()
                .id(BLOCK_ID)
                .gymId(GYM_ID)
                .label("CrossFit")
                .dayOfWeek(start.getDayOfWeek())
                .startTime(start.toLocalTime().withSecond(0).withNano(0))
                .endTime(LocalTime.of(23, 59))
                .capacity(10)
                .active(true)
                .build();
    }

    private LocalDate classDateOf(long minutesFromNow) {
        return ZonedDateTime.now(ZONE).plusMinutes(minutesFromNow).plusSeconds(30).toLocalDate();
    }

    private Gym gym(int bookingMinutes, int cancellationMinutes) {
        return Gym.builder()
                .id(GYM_ID)
                .bookingWindowMinutes(bookingMinutes)
                .cancellationWindowMinutes(cancellationMinutes)
                .showAttendeesToMembers(true)
                .build();
    }

    // --- cancelar usa SU ventana, no la de reservar ---

    private void stubCancel(GymBlock block, Gym gym, long minutesFromNow) {
        Reservation reservation = Reservation.builder()
                .id(RESERVATION_ID)
                .gymBlockId(BLOCK_ID)
                .memberId(MEMBER_ID)
                .classDate(classDateOf(minutesFromNow))
                .status(ReservationStatus.BOOKED)
                .build();
        when(reservationRepository.findByIdAndMemberId(RESERVATION_ID, MEMBER_ID)).thenReturn(Optional.of(reservation));
        when(gymBlockRepository.findById(BLOCK_ID)).thenReturn(Optional.of(block));
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(gym));
    }

    @Test
    void cancelFailsInsideTheCancellationWindowEvenIfBookingWindowWouldAllow() {
        // reservar se cierra 30 min antes, cancelar 120 min antes; la clase empieza en 60 min
        stubCancel(blockStartingIn(60), gym(30, 120), 60);

        assertThatThrownBy(() -> reservationService.cancel(MEMBER_ID, RESERVATION_ID))
                .isInstanceOf(BookingWindowClosedException.class)
                .hasMessage("Solo puedes cancelar hasta 2 h antes del inicio");
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void cancelSucceedsOutsideTheCancellationWindowEvenIfBookingWindowIsHuge() {
        // reservar se cierra 1 día antes (no aplica a cancelar); cancelar 30 min antes; clase en 60 min
        stubCancel(blockStartingIn(60), gym(1440, 30), 60);

        reservationService.cancel(MEMBER_ID, RESERVATION_ID);

        verify(reservationRepository).save(any(Reservation.class));
        verify(waitlistService).onSpotFreed(anyLong(), any(LocalDate.class));
    }

    // --- reservar usa SU ventana, no la de cancelar ---

    @Test
    void bookFailsInsideTheBookingWindowWithItsOwnMessage() {
        GymBlock block = blockStartingIn(20);
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(gym(30, 5)));
        when(gymBlockRepository.findByIdAndGymId(BLOCK_ID, GYM_ID)).thenReturn(Optional.of(block));

        assertThatThrownBy(() -> reservationService.book(
                        GYM_ID, MEMBER_ID, new ReservationCreateRequest(BLOCK_ID, classDateOf(20))))
                .isInstanceOf(BookingWindowClosedException.class)
                .hasMessage("Las reservas para esta clase cerraron 30 min antes del inicio");
    }

    @Test
    void bookWindowIgnoresTheCancellationWindow() {
        // cancelar necesita 5 h, pero reservar solo 10 min: a 60 min del inicio SÍ se puede reservar,
        // así que la validación de ventana pasa y falla recién más adelante (cierres/membresía),
        // nunca con el mensaje de ventana.
        GymBlock block = blockStartingIn(60);
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(gym(10, 300)));
        when(gymBlockRepository.findByIdAndGymId(BLOCK_ID, GYM_ID)).thenReturn(Optional.of(block));
        when(gymClosureService.closedReasonFor(anyLong(), anyLong(), any(LocalDate.class))).thenReturn("Feriado");

        assertThatThrownBy(() -> reservationService.book(
                        GYM_ID, MEMBER_ID, new ReservationCreateRequest(BLOCK_ID, classDateOf(60))))
                .isNotInstanceOf(BookingWindowClosedException.class);
    }

    // --- formato de minutos ---

    @Test
    void formatsMinutesAsHoursAndMinutes() {
        assertThat(ReservationService.formatMinutes(0)).isEqualTo("0 min");
        assertThat(ReservationService.formatMinutes(45)).isEqualTo("45 min");
        assertThat(ReservationService.formatMinutes(60)).isEqualTo("1 h");
        assertThat(ReservationService.formatMinutes(90)).isEqualTo("1 h 30 min");
        assertThat(ReservationService.formatMinutes(120)).isEqualTo("2 h");
        assertThat(ReservationService.formatMinutes(10_080)).isEqualTo("168 h");
    }

    // --- "Ver quién va": solo la vista del socio respeta el flag ---

    @Test
    void memberViewOfAttendeesIsForbiddenWhenTheGymHidesThem() {
        Gym hidden = gym(120, 120);
        hidden.setShowAttendeesToMembers(false);
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(hidden));

        assertThatThrownBy(() -> reservationService.getOccurrenceAttendeesForMember(
                        GYM_ID, BLOCK_ID, LocalDate.now(ZONE)))
                .isInstanceOf(AttendeesHiddenException.class);
        verify(gymBlockRepository, never()).findByIdAndGymId(anyLong(), anyLong());
    }

    @Test
    void adminViewOfAttendeesIgnoresTheFlag() {
        Gym hidden = gym(120, 120);
        hidden.setShowAttendeesToMembers(false);
        when(gymBlockRepository.findByIdAndGymId(BLOCK_ID, GYM_ID)).thenReturn(Optional.of(blockStartingIn(60)));
        when(reservationRepository.findByGymBlockIdAndClassDateAndStatus(
                        anyLong(), any(LocalDate.class), any(ReservationStatus.class)))
                .thenReturn(List.of());
        when(appUserRepository.findAllById(any())).thenReturn(List.of());

        // el panel del admin y la TV usan getOccurrenceAttendees, que no mira el flag
        assertThat(reservationService.getOccurrenceAttendees(GYM_ID, BLOCK_ID, LocalDate.now(ZONE))).isEmpty();
        verify(gymRepository, never()).findById(anyLong());
    }
}
