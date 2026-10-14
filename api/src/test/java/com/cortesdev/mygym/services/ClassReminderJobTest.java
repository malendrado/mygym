package com.cortesdev.mygym.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.PushSubscription;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.PushSubscriptionRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Recordatorio de clase: 60 min antes; si la reserva se hizo con menos de 60 min, 30 min antes; con menos de 30, nunca.
 * Los tiempos son relativos a "ahora" (el job lee el reloj), asi que se evita la franja de medianoche.
 */
@ExtendWith(MockitoExtension.class)
class ClassReminderJobTest {

    private static final ZoneId ZONE = ZoneId.of("America/Santiago");
    private static final Long GYM_ID = 5L;
    private static final Long BLOCK_ID = 9L;
    private static final Long MEMBER_ID = 30L;

    @Mock private ReservationRepository reservationRepository;
    @Mock private GymBlockRepository gymBlockRepository;
    @Mock private GymRepository gymRepository;
    @Mock private PushSubscriptionRepository subscriptionRepository;
    @Mock private PushNotificationService pushService;
    @InjectMocks private ClassReminderJob job;

    private ZonedDateTime now;
    private boolean gymActive = true;

    @BeforeEach
    void freezeTheDay() {
        now = ZonedDateTime.now(ZONE);
        // La clase y la reserva deben caer el mismo dia calendario que "hoy": no probar de 22:00 a 24:00.
        assumeTrue(now.getHour() < 21 && now.getHour() >= 1);
    }

    /** Reserva de una clase que empieza dentro de `startsInMinutes`, hecha `bookedMinutesBeforeStart` minutos antes del inicio. */
    private Reservation reservation(long startsInMinutes, long bookedMinutesBeforeStart) {
        ZonedDateTime start = now.plusMinutes(startsInMinutes);
        Reservation reservation = Reservation.builder()
                .id(77L)
                .gymBlockId(BLOCK_ID)
                .memberId(MEMBER_ID)
                .classDate(now.toLocalDate())
                .status(ReservationStatus.BOOKED)
                .createdAt(start.minusMinutes(bookedMinutesBeforeStart).toInstant())
                .build();
        GymBlock block = GymBlock.builder()
                .id(BLOCK_ID)
                .gymId(GYM_ID)
                .label("Funcional")
                .startTime(LocalTime.of(start.getHour(), start.getMinute()))
                .build();
        when(reservationRepository.findByClassDateAndStatusAndReminderPushedAtIsNull(any(LocalDate.class), any()))
                .thenReturn(List.of(reservation));
        when(gymBlockRepository.findAllById(any())).thenReturn(List.of(block));
        when(gymRepository.findAllById(any())).thenReturn(List.of(Gym.builder().id(GYM_ID).name("XaloGym").active(gymActive).build()));
        return reservation;
    }

    private void memberHasAPhone(boolean subscribed) {
        when(subscriptionRepository.findByUserIdIn(any()))
                .thenReturn(subscribed ? List.of(PushSubscription.builder().userId(MEMBER_ID).build()) : List.of());
    }

    private PushMessage sentMessage() {
        ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushService).notifyUsers(eq(List.of(MEMBER_ID)), captor.capture());
        return captor.getValue();
    }

    private static List<Long> eq(List<Long> value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }

    @Test
    void bookedDaysAheadRemindsAnHourBefore() {
        Reservation r = reservation(55, 60 * 24);
        memberHasAPhone(true);

        job.sendReminders();

        assertEquals("Tu clase empieza en 1 hora", sentMessage().title());
        assertNotNull(r.getReminderPushedAt());
    }

    @Test
    void notYetAnHourBeforeWaitsForALaterRun() {
        Reservation r = reservation(120, 60 * 24);
        memberHasAPhone(true);

        job.sendReminders();

        verify(pushService, never()).notifyUsers(any(), any());
        assertNull(r.getReminderPushedAt());
    }

    @Test
    void bookedWithin60MinutesRemindsHalfAnHourBefore() {
        Reservation r = reservation(25, 45);
        memberHasAPhone(true);

        job.sendReminders();

        assertEquals("Tu clase empieza en 30 minutos", sentMessage().title());
        assertNotNull(r.getReminderPushedAt());
    }

    @Test
    void bookedWithin60MinutesDoesNotRemindBeforeTheHalfHourMark() {
        Reservation r = reservation(40, 45);
        memberHasAPhone(true);

        job.sendReminders();

        verify(pushService, never()).notifyUsers(any(), any());
        assertNull(r.getReminderPushedAt());
    }

    @Test
    void bookedWithLessThan30MinutesIsNeverRemindedAndIsClosed() {
        Reservation r = reservation(20, 25);
        memberHasAPhone(true);

        job.sendReminders();

        verify(pushService, never()).notifyUsers(any(), any());
        assertNotNull(r.getReminderPushedAt()); // evaluada y descartada: no se vuelve a mirar
    }

    @Test
    void aMemberWithoutAPhoneIsNotMarkedSoALaterSubscriptionStillGetsIt() {
        Reservation r = reservation(55, 60 * 24);
        memberHasAPhone(false);

        job.sendReminders();

        verify(pushService, never()).notifyUsers(any(), any());
        assertNull(r.getReminderPushedAt());
    }

    @Test
    void aMemberWhoAlreadyCheckedInIsNotReminded() {
        Reservation r = reservation(55, 60 * 24);
        r.setCheckedInAt(Instant.now());
        memberHasAPhone(true);

        job.sendReminders();

        verify(pushService, never()).notifyUsers(any(), any());
        assertNotNull(r.getReminderPushedAt());
    }

    @Test
    void ifTheCronRanTooLateTheReminderIsDropped() {
        Reservation r = reservation(5, 60 * 24);
        memberHasAPhone(true);

        job.sendReminders();

        verify(pushService, never()).notifyUsers(any(), any());
        assertNotNull(r.getReminderPushedAt());
    }

    @Test
    void reminderMinutesDependOnHowFarAheadItWasBooked() {
        ZonedDateTime start = ZonedDateTime.now(ZONE).plusHours(5);
        for (long[] c : new long[][] {{600, 60}, {60, 60}, {59, 30}, {30, 30}, {29, 0}, {1, 0}}) {
            Reservation r = Reservation.builder().createdAt(start.minusMinutes(c[0]).toInstant()).build();
            assertEquals(c[1], ClassReminderJob.reminderMinutesFor(r, start), "reservada " + c[0] + " min antes");
        }
    }

    @Test
    void aPausedGymGetsNoReminders() {
        gymActive = false;
        Reservation r = reservation(55, 60 * 24);
        memberHasAPhone(true);

        job.sendReminders();

        verify(pushService, never()).notifyUsers(any(), any());
        assertNull(r.getReminderPushedAt());
    }
}
