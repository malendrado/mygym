package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.GymSubscription;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * UNICO lugar donde se calculan los periodos, el estado y los montos de la suscripcion de un gimnasio a mygym (nunca
 * duplicar estas cuentas). Mismo modelo que {@link MembershipPeriods}: un ancla (fecha de inicio) y los meses seguidos
 * pagados; cada borde se calcula desde el ancla para que el dia de corte no se corra (31 ene, 28 feb, 31 mar).
 *
 * <p>Linea de tiempo con corte el dia D (= ancla + meses pagados): D-3..D-1 avisos y "Renovar"; D "vence hoy" (sigue
 * activo); D+1 y D+2 gracia; D+3 suspendido.
 */
public final class GymBilling {

    public static final ZoneId ZONE = ZoneId.of("America/Santiago");
    /** Dias de gracia despues del vencimiento antes de suspender. */
    public static final int GRACE_DAYS = 2;
    /** Desde cuantos dias antes del vencimiento se ofrece "Renovar" (y se avisa). */
    public static final int RENEW_WINDOW_DAYS = 3;

    private static final double IVA_RATE = 0.19;
    /** Comision de Flow (2,89% + IVA) sobre el total cobrado: la paga el gimnasio cuando paga con Flow. */
    private static final double FLOW_RATE = 0.0289 * 1.19;

    private GymBilling() {}

    /** Resultado de aplicar un pago: nueva ancla y meses cubiertos desde ella. */
    public record Applied(LocalDate anchor, int paidPeriods) {}

    /** Desglose de un cobro mensual. {@code flowTotal} es lo que se cobra si paga con Flow. */
    public record Amounts(int net, int iva, int total, int flowFee, int flowTotal) {}

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /** Vencimiento (primer dia sin cubrir). Null si el gimnasio no se ha activado. */
    public static LocalDate dueDate(GymSubscription s) {
        return s.getAnchorDate() == null ? null : s.getAnchorDate().plusMonths(s.getPaidPeriods());
    }

    /** Primer dia en que queda suspendido si no pagó: vencimiento + gracia + 1. */
    public static LocalDate suspensionDate(LocalDate due) {
        return due.plusDays(GRACE_DAYS + 1L);
    }

    /** Estado que corresponde HOY segun las fechas. */
    public static String statusFor(GymSubscription s, LocalDate today) {
        if (s.isExempt()) {
            return GymSubscription.EXEMPT;
        }
        LocalDate due = dueDate(s);
        if (due == null) {
            return GymSubscription.NOT_STARTED;
        }
        if (!today.isAfter(due)) {
            return GymSubscription.ACTIVE;
        }
        return today.isBefore(suspensionDate(due)) ? GymSubscription.GRACE : GymSubscription.SUSPENDED;
    }

    /** Dias que faltan para el vencimiento (negativo si ya pasó). Null si no se ha activado. */
    public static Long daysToDue(GymSubscription s, LocalDate today) {
        LocalDate due = dueDate(s);
        return due == null ? null : java.time.temporal.ChronoUnit.DAYS.between(today, due);
    }

    /** ¿Se puede renovar ahora? Solo en los ultimos 3 dias, y mientras este vencido o suspendido. */
    public static boolean canRenew(GymSubscription s, LocalDate today) {
        if (s.isExempt() || s.getAnchorDate() == null) {
            return false;
        }
        Long days = daysToDue(s, today);
        return days != null && days <= RENEW_WINDOW_DAYS;
    }

    /**
     * Aplica un pago hecho hoy y devuelve la nueva ancla y los meses cubiertos.
     *
     * <ul>
     *   <li>Al dia o en gracia: suma un mes, el dia de corte no cambia.
     *   <li>Suspendido y paga antes del siguiente corte: cubre el mes que debia (pierde los dias suspendido).
     *   <li>Suspendido y paga despues del siguiente corte: cubre desde el proximo corte (lo intermedio queda regalado).
     * </ul>
     */
    public static Applied applyPayment(LocalDate anchor, int paidPeriods, LocalDate today) {
        LocalDate due = anchor.plusMonths(paidPeriods);
        if (today.isBefore(suspensionDate(due))) {
            return new Applied(anchor, paidPeriods + 1);
        }
        if (today.isBefore(anchor.plusMonths(paidPeriods + 1L))) {
            return new Applied(anchor, paidPeriods + 1);
        }
        int k = paidPeriods + 1;
        while (anchor.plusMonths(k).isBefore(today)) {
            k++;
        }
        return new Applied(anchor.plusMonths(k), 1);
    }

    /** Primer dia de corte posterior a hoy (o la fecha de inicio si aun no parte): desde ahi rige un cambio de plan. */
    public static LocalDate nextCutAfter(LocalDate anchor, LocalDate today) {
        int k = 0;
        while (!anchor.plusMonths(k).isAfter(today)) {
            k++;
        }
        return anchor.plusMonths(k);
    }

    public static Amounts amounts(int netClp) {
        int iva = (int) Math.round(netClp * IVA_RATE);
        int total = netClp + iva;
        int flowFee = (int) Math.round(total * FLOW_RATE);
        return new Amounts(netClp, iva, total, flowFee, total + flowFee);
    }
}
