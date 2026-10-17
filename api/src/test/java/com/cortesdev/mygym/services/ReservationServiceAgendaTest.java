package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.dto.AgendaOccurrence;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.services.exception.InvalidAttendanceException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unidades puras (Mockito, sin DB): la Agenda del admin (cupos, cierres, espera, clases sin reservas). */
@ExtendWith(MockitoExtension.class)
class ReservationServiceAgendaTest {

    private static final Long GYM_ID = 5L;
    // Lunes 12 → domingo 18 de octubre de 2026.
    private static final LocalDate FROM = LocalDate.of(2026, 10, 12);
    private static final LocalDate TO = LocalDate.of(2026, 10, 18);
    private static final LocalDate TUESDAY = LocalDate.of(2026, 10, 13);

    @Mock private GymBlockRepository gymBlockRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private GymClosureService gymClosureService;
    @Mock private WaitlistService waitlistService;
    @InjectMocks private ReservationService reservationService;

    private GymBlock block(long id, DayOfWeek day, int startHour, int capacity, boolean active) {
        return GymBlock.builder()
                .id(id)
                .gymId(GYM_ID)
                .label("Clase " + id)
                .dayOfWeek(day)
                .startTime(LocalTime.of(startHour, 0))
                .endTime(LocalTime.of(startHour + 1, 0))
                .capacity(capacity)
                .active(active)
                .build();
    }

    private Reservation booked(long id, long blockId, long memberId, LocalDate date) {
        return Reservation.builder()
                .id(id)
                .gymBlockId(blockId)
                .memberId(memberId)
                .classDate(date)
                .status(ReservationStatus.BOOKED)
                .build();
    }

    private AppUser member(long id, String name) {
        return AppUser.builder().id(id).name(name).email(name.toLowerCase() + "@test.cl").build();
    }

    @Test
    void theMemberCalendarRejectsAnAbsurdRangeBeforeTouchingTheDatabase() {
        assertThatThrownBy(() -> reservationService.listOccurrences(
                        GYM_ID, 9L, LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31)))
                .isInstanceOf(InvalidAttendanceException.class);
        assertThatThrownBy(() -> reservationService.listOccurrences(GYM_ID, 9L, TO, FROM))
                .isInstanceOf(InvalidAttendanceException.class);
        org.mockito.Mockito.verifyNoInteractions(gymBlockRepository, reservationRepository);
    }

    private void givenNoClosuresNoWaiting() {
        when(gymClosureService.scheduleFor(eq(GYM_ID), any(), any()))
                .thenReturn(new GymClosureService.ClosureSchedule(Set.of(), Set.of()));
        when(waitlistService.countWaitingByOccurrence(anyCollection(), any(), any())).thenReturn(Map.of());
    }

    @Test
    void classWithoutReservationsStillAppearsWithZeroTaken() {
        when(gymBlockRepository.findByGymId(GYM_ID)).thenReturn(List.of(block(1, DayOfWeek.TUESDAY, 19, 12, true)));
        when(reservationRepository.findByGymBlockIdInAndClassDateBetweenAndStatus(
                        anyList(), eq(FROM), eq(TO), eq(ReservationStatus.BOOKED)))
                .thenReturn(List.of());
        givenNoClosuresNoWaiting();

        List<AgendaOccurrence> agenda = reservationService.getAgenda(GYM_ID, FROM, TO);

        assertThat(agenda).hasSize(1);
        assertThat(agenda.get(0).classDate()).isEqualTo(TUESDAY);
        assertThat(agenda.get(0).capacity()).isEqualTo(12);
        assertThat(agenda.get(0).taken()).isZero();
        assertThat(agenda.get(0).attendees()).isEmpty();
        assertThat(agenda.get(0).closed()).isFalse();
        assertThat(agenda.get(0).closedReason()).isNull();
    }

    @Test
    void takenCountsOnlyBookedAttendeesOfThatOccurrence() {
        when(gymBlockRepository.findByGymId(GYM_ID)).thenReturn(List.of(block(1, DayOfWeek.TUESDAY, 19, 2, true)));
        when(reservationRepository.findByGymBlockIdInAndClassDateBetweenAndStatus(
                        anyList(), eq(FROM), eq(TO), eq(ReservationStatus.BOOKED)))
                .thenReturn(List.of(booked(10, 1, 100, TUESDAY), booked(11, 1, 101, TUESDAY)));
        when(appUserRepository.findAllById(any())).thenReturn(List.of(member(100, "Ana"), member(101, "Pedro")));
        givenNoClosuresNoWaiting();

        AgendaOccurrence occurrence = reservationService.getAgenda(GYM_ID, FROM, TO).get(0);

        assertThat(occurrence.taken()).isEqualTo(2);
        assertThat(occurrence.attendees()).extracting(a -> a.member().getName()).containsExactly("Ana", "Pedro");
    }

    @Test
    void wholeDayClosureMarksClosedWithReason() {
        when(gymBlockRepository.findByGymId(GYM_ID)).thenReturn(List.of(block(1, DayOfWeek.TUESDAY, 19, 12, true)));
        when(reservationRepository.findByGymBlockIdInAndClassDateBetweenAndStatus(
                        anyList(), eq(FROM), eq(TO), eq(ReservationStatus.BOOKED)))
                .thenReturn(List.of());
        when(gymClosureService.scheduleFor(GYM_ID, FROM, TO))
                .thenReturn(new GymClosureService.ClosureSchedule(
                        Set.of(TUESDAY), Set.of(), Map.of(TUESDAY, "Corte de luz"), Map.of()));
        when(waitlistService.countWaitingByOccurrence(anyCollection(), any(), any())).thenReturn(Map.of());

        AgendaOccurrence occurrence = reservationService.getAgenda(GYM_ID, FROM, TO).get(0);

        assertThat(occurrence.closed()).isTrue();
        assertThat(occurrence.closedReason()).isEqualTo("Corte de luz");
    }

    @Test
    void blockSpecificClosureOnlyClosesThatBlock() {
        when(gymBlockRepository.findByGymId(GYM_ID))
                .thenReturn(List.of(block(1, DayOfWeek.TUESDAY, 19, 12, true), block(2, DayOfWeek.TUESDAY, 20, 12, true)));
        when(reservationRepository.findByGymBlockIdInAndClassDateBetweenAndStatus(
                        anyList(), eq(FROM), eq(TO), eq(ReservationStatus.BOOKED)))
                .thenReturn(List.of());
        when(gymClosureService.scheduleFor(GYM_ID, FROM, TO))
                .thenReturn(new GymClosureService.ClosureSchedule(
                        Set.of(), Set.of("2|" + TUESDAY), Map.of(), Map.of("2|" + TUESDAY, "Mantención")));
        when(waitlistService.countWaitingByOccurrence(anyCollection(), any(), any())).thenReturn(Map.of());

        List<AgendaOccurrence> agenda = reservationService.getAgenda(GYM_ID, FROM, TO);

        assertThat(agenda).extracting(AgendaOccurrence::gymBlockId).containsExactly(1L, 2L);
        assertThat(agenda).extracting(AgendaOccurrence::closed).containsExactly(false, true);
    }

    @Test
    void waitlistCountComesFromTheOccurrenceKey() {
        when(gymBlockRepository.findByGymId(GYM_ID)).thenReturn(List.of(block(1, DayOfWeek.TUESDAY, 19, 1, true)));
        when(reservationRepository.findByGymBlockIdInAndClassDateBetweenAndStatus(
                        anyList(), eq(FROM), eq(TO), eq(ReservationStatus.BOOKED)))
                .thenReturn(List.of());
        when(gymClosureService.scheduleFor(GYM_ID, FROM, TO))
                .thenReturn(new GymClosureService.ClosureSchedule(Set.of(), Set.of()));
        when(waitlistService.countWaitingByOccurrence(anyCollection(), eq(FROM), eq(TO)))
                .thenReturn(Map.of("1|" + TUESDAY, 3));

        assertThat(reservationService.getAgenda(GYM_ID, FROM, TO).get(0).waitlistCount()).isEqualTo(3);
    }

    @Test
    void inactiveBlockAppearsOnlyIfItHadReservations() {
        GymBlock withBookings = block(1, DayOfWeek.TUESDAY, 19, 12, false);
        GymBlock empty = block(2, DayOfWeek.TUESDAY, 20, 12, false);
        when(gymBlockRepository.findByGymId(GYM_ID)).thenReturn(List.of(withBookings, empty));
        when(reservationRepository.findByGymBlockIdInAndClassDateBetweenAndStatus(
                        anyList(), eq(FROM), eq(TO), eq(ReservationStatus.BOOKED)))
                .thenReturn(List.of(booked(10, 1, 100, TUESDAY)));
        when(appUserRepository.findAllById(any())).thenReturn(List.of(member(100, "Ana")));
        givenNoClosuresNoWaiting();

        List<AgendaOccurrence> agenda = reservationService.getAgenda(GYM_ID, FROM, TO);

        assertThat(agenda).extracting(AgendaOccurrence::gymBlockId).containsExactly(1L);
    }

    @Test
    void occurrencesAreSortedByDateThenStartTime() {
        when(gymBlockRepository.findByGymId(GYM_ID))
                .thenReturn(List.of(
                        block(1, DayOfWeek.WEDNESDAY, 7, 10, true),
                        block(2, DayOfWeek.TUESDAY, 20, 10, true),
                        block(3, DayOfWeek.TUESDAY, 7, 10, true)));
        when(reservationRepository.findByGymBlockIdInAndClassDateBetweenAndStatus(
                        anyList(), eq(FROM), eq(TO), eq(ReservationStatus.BOOKED)))
                .thenReturn(List.of());
        givenNoClosuresNoWaiting();

        assertThat(reservationService.getAgenda(GYM_ID, FROM, TO))
                .extracting(AgendaOccurrence::gymBlockId)
                .containsExactly(3L, 2L, 1L);
    }

    @Test
    void gymWithoutBlocksReturnsEmptyWithoutQueryingReservations() {
        when(gymBlockRepository.findByGymId(GYM_ID)).thenReturn(List.of());

        assertThat(reservationService.getAgenda(GYM_ID, FROM, TO)).isEmpty();
    }

    @Test
    void rangeLongerThan31DaysIsRejected() {
        assertThatThrownBy(() -> reservationService.getAgenda(GYM_ID, FROM, FROM.plusDays(31)))
                .isInstanceOf(InvalidAttendanceException.class)
                .hasMessageContaining("31 días");
    }

    @Test
    void reversedRangeIsRejected() {
        assertThatThrownBy(() -> reservationService.getAgenda(GYM_ID, TO, FROM))
                .isInstanceOf(InvalidAttendanceException.class);
    }
}
