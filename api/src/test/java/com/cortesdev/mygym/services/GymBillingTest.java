package com.cortesdev.mygym.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cortesdev.mygym.models.GymSubscription;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Linea de tiempo y reglas de pago de la suscripcion de un gimnasio. Todas las fechas son de Chile. */
class GymBillingTest {

    private static GymSubscription sub(LocalDate anchor, int periods) {
        return GymSubscription.builder().anchorDate(anchor).paidPeriods(periods).billingStatus("ACTIVE").build();
    }

    @Test
    void theDueDateIsTheAnchorPlusThePaidMonths() {
        assertEquals(LocalDate.of(2026, 11, 15), GymBilling.dueDate(sub(LocalDate.of(2026, 10, 15), 1)));
        assertEquals(LocalDate.of(2027, 1, 15), GymBilling.dueDate(sub(LocalDate.of(2026, 10, 15), 3)));
        assertNull(GymBilling.dueDate(GymSubscription.builder().build()));
    }

    @Test
    void theCutDayDoesNotDriftAfterShortMonths() {
        GymSubscription s = sub(LocalDate.of(2026, 1, 31), 1);
        assertEquals(LocalDate.of(2026, 2, 28), GymBilling.dueDate(s));
        s.setPaidPeriods(2);
        assertEquals(LocalDate.of(2026, 3, 31), GymBilling.dueDate(s));
    }

    @Test
    void timelineAroundTheDueDate() {
        GymSubscription s = sub(LocalDate.of(2026, 10, 15), 1); // vence el 15/11
        assertEquals("ACTIVE", GymBilling.statusFor(s, LocalDate.of(2026, 11, 12)));
        assertEquals("ACTIVE", GymBilling.statusFor(s, LocalDate.of(2026, 11, 15))); // vence hoy: sigue activo
        assertEquals("GRACE", GymBilling.statusFor(s, LocalDate.of(2026, 11, 16)));
        assertEquals("GRACE", GymBilling.statusFor(s, LocalDate.of(2026, 11, 17)));
        assertEquals("SUSPENDED", GymBilling.statusFor(s, LocalDate.of(2026, 11, 18)));
    }

    @Test
    void anExemptGymIsNeverSuspendedAndAnUnstartedOneIsNotStarted() {
        GymSubscription exempt = sub(LocalDate.of(2020, 1, 1), 1);
        exempt.setExempt(true);
        assertEquals("EXEMPT", GymBilling.statusFor(exempt, LocalDate.of(2026, 11, 20)));
        assertFalse(GymBilling.canRenew(exempt, LocalDate.of(2026, 11, 20)));

        assertEquals("NOT_STARTED", GymBilling.statusFor(GymSubscription.builder().build(), LocalDate.of(2026, 11, 20)));
    }

    @Test
    void renewIsOnlyOfferedInTheLastThreeDays() {
        GymSubscription s = sub(LocalDate.of(2026, 10, 15), 1); // vence 15/11
        assertFalse(GymBilling.canRenew(s, LocalDate.of(2026, 11, 11)));
        assertTrue(GymBilling.canRenew(s, LocalDate.of(2026, 11, 12)));
        assertTrue(GymBilling.canRenew(s, LocalDate.of(2026, 11, 15)));
        assertTrue(GymBilling.canRenew(s, LocalDate.of(2026, 11, 20))); // ya suspendido: sigue pudiendo pagar
    }

    @Test
    void payingOnTimeOrInGraceAddsAMonthKeepingTheCutDay() {
        GymBilling.Applied early = GymBilling.applyPayment(LocalDate.of(2026, 10, 15), 1, LocalDate.of(2026, 11, 13));
        assertEquals(new GymBilling.Applied(LocalDate.of(2026, 10, 15), 2), early);

        GymBilling.Applied grace = GymBilling.applyPayment(LocalDate.of(2026, 10, 15), 1, LocalDate.of(2026, 11, 17));
        assertEquals(new GymBilling.Applied(LocalDate.of(2026, 10, 15), 2), grace);
    }

    @Test
    void aShortSuspensionCoversTheMonthOwedAndGivesNothingAway() {
        // Vence el 15/11, se suspende el 18/11, paga el 20/11: cubre 15/11 a 15/12.
        GymBilling.Applied a = GymBilling.applyPayment(LocalDate.of(2026, 10, 15), 1, LocalDate.of(2026, 11, 20));
        assertEquals(new GymBilling.Applied(LocalDate.of(2026, 10, 15), 2), a);
        assertEquals(LocalDate.of(2026, 12, 15), a.anchor().plusMonths(a.paidPeriods()));
    }

    @Test
    void aLongSuspensionCoversFromTheNextCut() {
        // Vence el 15/11, paga el 3/1: cubre del 15/1 al 15/2 y lo intermedio queda regalado.
        GymBilling.Applied a = GymBilling.applyPayment(LocalDate.of(2026, 10, 15), 1, LocalDate.of(2027, 1, 3));
        assertEquals(new GymBilling.Applied(LocalDate.of(2027, 1, 15), 1), a);
        assertEquals(LocalDate.of(2027, 2, 15), a.anchor().plusMonths(a.paidPeriods()));
    }

    @Test
    void payingExactlyOnTheNextCutDayStartsThatSameDay() {
        GymBilling.Applied a = GymBilling.applyPayment(LocalDate.of(2026, 10, 15), 1, LocalDate.of(2026, 12, 15));
        assertEquals(new GymBilling.Applied(LocalDate.of(2026, 12, 15), 1), a);
    }

    @Test
    void theNextCutIsTheFirstOneAfterTodayOrTheStartDateIfItIsInTheFuture() {
        assertEquals(LocalDate.of(2026, 12, 15), GymBilling.nextCutAfter(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 20)));
        assertEquals(LocalDate.of(2026, 11, 15), GymBilling.nextCutAfter(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 15)));
        assertEquals(LocalDate.of(2026, 12, 1), GymBilling.nextCutAfter(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 11, 20)));
    }

    @Test
    void amountsAddVatAndTheFlowFeeOnTheTotal() {
        GymBilling.Amounts a = GymBilling.amounts(29_990);
        assertEquals(29_990, a.net());
        assertEquals(5_698, a.iva());
        assertEquals(35_688, a.total());
        assertEquals(1_227, a.flowFee()); // 35.688 x 2,89% x 1,19
        assertEquals(a.total() + a.flowFee(), a.flowTotal());
    }
}
