package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

/**
 * El cron ya no carga a todos los socios que alguna vez pagaron: pide solo los pagos que caen en una ventana. Esta prueba
 * comprueba, para dos años de fechas (incluidos fines de mes cortos y bisiestos), que ningún socio al que el cron debe avisar
 * (período = pago + 1 mes, vence entre hoy y dentro de 3 días) quede fuera de la ventana.
 */
class MembershipReminderWindowTest {

    private static final ZoneId ZONE = ZoneId.of("America/Santiago");

    @Test
    void everyMemberWhoseMonthEndsInZeroToThreeDaysIsInsideTheQueryWindow() {
        ZonedDateTime firstPayment = ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, ZONE);
        for (int paidOffset = 0; paidOffset < 800; paidOffset++) {
            for (int hour : new int[] {0, 9, 23}) {
                ZonedDateTime paidAt = firstPayment.plusDays(paidOffset).withHour(hour).withMinute(59);
                LocalDate expiry = paidAt.plusMonths(1).toLocalDate();
                // Las 3 horas a las que podría correr el cron en cada día de aviso (el real es a las 09:00).
                for (int daysBefore = 0; daysBefore <= 3; daysBefore++) {
                    ZonedDateTime now = expiry.minusDays(daysBefore).atTime(9, 0).atZone(ZONE);
                    ZonedDateTime from = now.minusDays(MembershipReminderJob.PAID_AT_WINDOW_FROM_DAYS);
                    ZonedDateTime to = now.minusDays(MembershipReminderJob.PAID_AT_WINDOW_TO_DAYS);
                    long daysUntilExpiry = ChronoUnit.DAYS.between(now.toLocalDate(), expiry);
                    assertThat(daysUntilExpiry).isBetween(0L, 3L);
                    assertThat(paidAt.toInstant())
                            .as("pagado %s, corre el cron %s", paidAt, now)
                            .isBetween(from.toInstant(), to.toInstant());
                }
            }
        }
    }

    @Test
    void theWindowDoesNotReachMembersWhoseMonthEndsMuchLater() {
        ZonedDateTime now = ZonedDateTime.of(2027, 3, 10, 9, 0, 0, 0, ZONE);
        ZonedDateTime from = now.minusDays(MembershipReminderJob.PAID_AT_WINDOW_FROM_DAYS);
        // Alguien que pagó hace 5 días (le quedan ~25) y alguien que pagó hace 3 meses no deben entrar.
        assertThat(now.minusDays(5).toInstant()).isAfter(now.minusDays(MembershipReminderJob.PAID_AT_WINDOW_TO_DAYS).toInstant());
        assertThat(now.minusMonths(3).toInstant()).isBefore(from.toInstant());
    }
}
