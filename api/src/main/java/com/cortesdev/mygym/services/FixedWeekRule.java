package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.repositories.MemberFixedClassRepository;
import com.cortesdev.mygym.services.exception.BookingWindowClosedException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Regla "socios con clases fijas solo reservan y cancelan en la semana en curso" (Gym.fixedMembersCurrentWeekOnly,
 * V42). Aplica únicamente a quien tiene clases fijas precargadas por el admin; la semana va de lunes a domingo en la
 * zona del gym. Al cancelar, el cupo vuelve solo (el cupo cuenta reservas BOOKED). El admin no pasa por acá.
 */
@Component
@RequiredArgsConstructor
public class FixedWeekRule {

    private static final ZoneId GYM_ZONE = ZoneId.of("America/Santiago");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final MemberFixedClassRepository memberFixedClassRepository;

    /** @param action lo que intenta hacer, en infinitivo ("reservar", "cancelar", "anotarte en la espera de"). */
    public void assertAllowed(Gym gym, Long memberId, LocalDate classDate, String action) {
        if (!gym.isFixedMembersCurrentWeekOnly()) {
            return;
        }
        LocalDate today = LocalDate.now(GYM_ZONE);
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);
        if (!classDate.isBefore(monday) && !classDate.isAfter(sunday)) {
            return;
        }
        if (!memberFixedClassRepository.existsByMemberId(memberId)) {
            return;
        }
        throw new BookingWindowClosedException("Como tienes clases fijas, solo puedes " + action
                + " clases de esta semana (hasta el domingo " + DATE_FORMAT.format(sunday) + ")");
    }
}
