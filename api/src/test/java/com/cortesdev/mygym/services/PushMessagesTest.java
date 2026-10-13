package com.cortesdev.mygym.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PushMessagesTest {

    private final Gym gym = Gym.builder().id(1L).name("XaloGym").build();
    private final GymBlock block = GymBlock.builder().id(9L).gymId(1L).label("Funcional").capacity(10).startTime(LocalTime.of(19, 0)).build();
    private final LocalDate wednesday = LocalDate.of(2026, 10, 14);

    @Test
    void waitlistHeadStartTellsTheMinutesOfAdvantageAndIsUrgent() {
        PushMessage m = PushMessages.waitlistHeadStart(block, wednesday, 30);
        assertEquals("¡Se liberó un cupo!", m.title());
        assertEquals("Funcional · miércoles 14 a las 19:00. Tienes 30 min de ventaja.", m.body());
        assertEquals("waitlist-9-2026-10-14", m.tag());
        assertEquals("/member", m.url());
        assertEquals(PushMessage.Urgency.HIGH, m.urgency());
        assertEquals(1800, m.ttlSeconds());
    }

    @Test
    void waitlistRestOfTheListSharesTheTagSoItReplacesTheEarlierOne() {
        assertEquals(
                PushMessages.waitlistHeadStart(block, wednesday, 30).tag(),
                PushMessages.waitlistSpotOpen(block, wednesday).tag());
    }

    @Test
    void classReminderSaysAnHourOrHalfAnHour() {
        assertEquals("Tu clase empieza en 1 hora", PushMessages.classReminder(gym, block, wednesday, 60, 5L).title());
        assertEquals("Tu clase empieza en 30 minutos", PushMessages.classReminder(gym, block, wednesday, 30, 5L).title());
        assertEquals("Funcional a las 19:00 en XaloGym", PushMessages.classReminder(gym, block, wednesday, 60, 5L).body());
        assertEquals("reminder-5", PushMessages.classReminder(gym, block, wednesday, 60, 5L).tag());
    }

    @Test
    void closureCancellationListsTheFirstClassesAndCountsTheRest() {
        PushMessage one = PushMessages.closureCancellation(gym, "corte de luz", List.of("Funcional — miércoles 14 de octubre"), 3L);
        assertEquals("XaloGym canceló tu clase", one.title());
        assertEquals("Corte de luz: Funcional — miércoles 14 de octubre", one.body());

        PushMessage many = PushMessages.closureCancellation(gym, "corte de luz", List.of("A", "B", "C", "D"), 3L);
        assertEquals("XaloGym canceló tus clases", many.title());
        assertEquals("Corte de luz: A, B y 2 más", many.body());
    }

    @Test
    void membershipMessages() {
        assertEquals("Tu plan vence mañana", PushMessages.membershipExpiringSoon(gym, 1).title());
        assertEquals("Tu plan vence en 3 días", PushMessages.membershipExpiringSoon(gym, 3).title());
        assertEquals("Tu membresía venció", PushMessages.membershipExpired(gym).title());
        assertTrue(PushMessages.classesExhausted("30/10/2026").body().contains("30/10/2026"));
    }

    @Test
    void staffCancellationCountsSeveralInTheSameNotification() {
        PushMessage one = PushMessages.memberCancelled(block, wednesday, "Ana Soto", 1, 3, 0);
        assertEquals("Cancelación · Funcional 19:00", one.title());
        assertEquals("Ana Soto canceló (miércoles 14). Quedan 3 cupos.", one.body());
        assertEquals("cancel-9-2026-10-14", one.tag());
        assertEquals("/gym-admin", one.url());

        PushMessage three = PushMessages.memberCancelled(block, wednesday, "Ana Soto", 3, 1, 2);
        assertEquals("3 cancelaciones · Funcional 19:00", three.title());
        assertEquals("Ana Soto y 2 más cancelaron (miércoles 14). Queda 1 cupo · 2 en espera.", three.body());
        assertEquals(one.tag(), three.tag());
    }
}
