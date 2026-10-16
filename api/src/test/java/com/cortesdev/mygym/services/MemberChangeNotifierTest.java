package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.GymClosure;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.repositories.ReservationRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Avisos al socio cuando el equipo le cambia algo (Mockito, sin DB). */
@ExtendWith(MockitoExtension.class)
class MemberChangeNotifierTest {

    private static final Long GYM_ID = 4L;
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Santiago"));

    @Mock private PushNotificationService pushService;
    @Mock private ReservationRepository reservationRepository;
    @InjectMocks private MemberChangeNotifier notifier;

    private final Gym activeGym = Gym.builder().id(GYM_ID).name("XaloGym").active(true).build();
    private final Gym pausedGym = Gym.builder().id(GYM_ID).name("XaloGym").active(false).build();
    private final GymBlock block = GymBlock.builder()
            .id(7L).gymId(GYM_ID).label("Funcional").dayOfWeek(DayOfWeek.WEDNESDAY)
            .startTime(LocalTime.of(19, 30)).endTime(LocalTime.of(20, 30)).capacity(10).active(true).build();

    private Reservation reservation(long id, long memberId, LocalDate date) {
        return Reservation.builder().id(id).gymBlockId(7L).memberId(memberId).classDate(date).status(ReservationStatus.BOOKED).build();
    }

    private PushMessage sentTo(Collection<Long> expectedUsers) {
        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushService).notifyUsers(eq(expectedUsers), message.capture());
        return message.getValue();
    }

    // ---- A) se levanta un cierre ----

    @Test
    void liftingAClosureTellsEachAffectedMemberOnceThatTheyCanBookAgain() {
        GymClosure closure = GymClosure.builder().id(3L).gymId(GYM_ID).endDate(TODAY.plusDays(2)).build();
        when(reservationRepository.findByCancelledByClosureId(3L))
                .thenReturn(List.of(reservation(1, 20, TODAY.plusDays(1)), reservation(2, 20, TODAY.plusDays(2)), reservation(3, 21, TODAY.plusDays(1))));

        notifier.closureLifted(activeGym, closure);

        PushMessage message = sentTo(List.of(20L, 21L));
        assertThat(message.title()).isEqualTo("XaloGym vuelve a abrir");
        assertThat(message.body()).contains("volver a reservar");
    }

    @Test
    void liftingAClosureThatAlreadyEndedTellsNobody() {
        GymClosure closure = GymClosure.builder().id(3L).gymId(GYM_ID).endDate(TODAY.minusDays(1)).build();

        notifier.closureLifted(activeGym, closure);

        verifyNoInteractions(pushService, reservationRepository);
    }

    // ---- B) el equipo cancela una reserva ----

    @Test
    void whenStaffCancelsAReservationTheMemberIsToldWhichClass() {
        notifier.reservationCancelledByStaff(activeGym, block, reservation(9, 20, TODAY.plusDays(1)));

        PushMessage message = sentTo(List.of(20L));
        assertThat(message.title()).isEqualTo("Cancelamos tu reserva");
        assertThat(message.body()).contains("Funcional").contains("19:30");
    }

    // ---- C) cambia el horario ----

    @Test
    void reschedulingAClassTellsEveryMemberWithABookingTheNewAndOldTime() {
        when(reservationRepository.findByGymBlockIdAndStatusAndClassDateGreaterThanEqual(eq(7L), eq(ReservationStatus.BOOKED), any()))
                .thenReturn(List.of(reservation(1, 20, TODAY.plusDays(3)), reservation(2, 21, TODAY.plusDays(3)), reservation(3, 20, TODAY.plusDays(10))));

        notifier.blockRescheduled(activeGym, block, DayOfWeek.THURSDAY, LocalTime.of(19, 0));

        PushMessage message = sentTo(List.of(20L, 21L));
        assertThat(message.title()).isEqualTo("Cambió el horario de Funcional");
        assertThat(message.body()).contains("miércoles").contains("19:30").contains("jueves").contains("19:00");
    }

    @Test
    void reschedulingAClassWithoutBookingsSendsNothing() {
        when(reservationRepository.findByGymBlockIdAndStatusAndClassDateGreaterThanEqual(eq(7L), eq(ReservationStatus.BOOKED), any()))
                .thenReturn(List.of());

        notifier.blockRescheduled(activeGym, block, DayOfWeek.THURSDAY, LocalTime.of(19, 0));

        verify(pushService).notifyUsers(eq(List.of()), any());
    }

    // ---- D) clase desactivada o eliminada ----

    @Test
    void aCancelledClassTellsEachMemberTheirOwnDays() {
        when(reservationRepository.findByGymBlockIdAndStatusAndClassDateGreaterThanEqual(eq(7L), eq(ReservationStatus.BOOKED), any()))
                .thenReturn(List.of(reservation(1, 20, TODAY.plusDays(1)), reservation(2, 20, TODAY.plusDays(8)), reservation(3, 21, TODAY.plusDays(2))));

        notifier.blockCancelled(activeGym, block);

        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushService).notifyUsers(eq(List.of(20L)), message.capture());
        verify(pushService).notifyUsers(eq(List.of(21L)), any());
        assertThat(message.getValue().title()).isEqualTo("Funcional ya no se dicta");
    }

    // ---- E) quitan el plan ----

    @Test
    void removingAPlanTellsTheMemberHowManyReservationsWereCancelled() {
        notifier.planRemoved(activeGym, 20L, 2);

        PushMessage message = sentTo(List.of(20L));
        assertThat(message.title()).isEqualTo("Tu plan fue quitado");
        assertThat(message.body()).contains("XaloGym").contains("2 reservas");
    }

    @Test
    void removingAPlanWithoutReservationsDoesNotMentionThem() {
        notifier.planRemoved(activeGym, 20L, 0);

        assertThat(sentTo(List.of(20L)).body()).doesNotContain("reserva");
    }

    // ---- reglas comunes ----

    @Test
    void aPausedGymSendsNothingAtAll() {
        GymClosure closure = GymClosure.builder().id(3L).gymId(GYM_ID).endDate(TODAY.plusDays(2)).build();

        notifier.closureLifted(pausedGym, closure);
        notifier.reservationCancelledByStaff(pausedGym, block, reservation(9, 20, TODAY.plusDays(1)));
        notifier.blockRescheduled(pausedGym, block, DayOfWeek.THURSDAY, LocalTime.of(19, 0));
        notifier.blockCancelled(pausedGym, block);
        notifier.planRemoved(pausedGym, 20L, 1);

        verifyNoInteractions(pushService, reservationRepository);
    }

    @Test
    void aFailingPushNeverBreaksTheOperationThatTriggeredIt() {
        org.mockito.Mockito.doThrow(new RuntimeException("push caido")).when(pushService).notifyUsers(any(), any());

        notifier.planRemoved(activeGym, 20L, 1); // no lanza

        verify(pushService, times(1)).notifyUsers(any(), any());
        verify(pushService, never()).notifyLater(any());
    }
}
