package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * ÚNICO lugar donde se calculan los períodos de membresía de un socio (nunca duplicar estas cuentas).
 *
 * Modelo: una "cadena" de meses calendario pagados seguidos, anclada en {@code COALESCE(periodStart, paidAt)} y de
 * {@code COALESCE(paidPeriods, 1)} meses. El período k (1..N) es [ancla + (k-1) meses, ancla + k meses): cada borde se
 * calcula desde el ancla y NUNCA sumando "un mes" sobre un borde ya ajustado (31 ene -> 28 feb -> 31 mar sin deriva).
 * El período ACTUAL es el k que contiene el instante de consulta; si k < N, los períodos k+1..N están "en cola"
 * (pagados por adelantado). El vencimiento FINAL es ancla + N meses y es el que ve el socio, el que decide el estado
 * (activo / por vencer / vencido) y el que usan los avisos.
 *
 * paidAt es siempre el instante real del último pago; un pago sin renovación anticipada (plan vencido, nunca pagado,
 * otro plan o fecha elegida por el admin) arranca una cadena nueva: paidAt = inicio, periodStart/paidPeriods = null.
 */
public final class MembershipPeriods {

    public static final ZoneId GYM_ZONE = ZoneId.of("America/Santiago");

    private MembershipPeriods() {}

    /** Ancla de la cadena, o null si el socio nunca pagó. */
    public static ZonedDateTime anchor(AppUser user) {
        Instant anchor = user.getPeriodStart() != null ? user.getPeriodStart() : user.getPaidAt();
        return anchor == null ? null : anchor.atZone(GYM_ZONE);
    }

    /** Meses seguidos que cubre la cadena (N >= 1). */
    public static int paidPeriods(AppUser user) {
        Integer n = user.getPaidPeriods();
        return n == null || n < 1 ? 1 : n;
    }

    /** Inicio del período k (1-based). */
    public static ZonedDateTime startOf(ZonedDateTime anchor, int k) {
        return anchor.plusMonths(k - 1L);
    }

    /** Fin (exclusivo) del período k (1-based), siempre calculado desde el ancla. */
    public static ZonedDateTime endOf(ZonedDateTime anchor, int k) {
        return anchor.plusMonths(k);
    }

    /** Vencimiento FINAL (último período pagado, cola incluida); null si nunca pagó. */
    public static ZonedDateTime finalEnd(AppUser user) {
        ZonedDateTime anchor = anchor(user);
        return anchor == null ? null : endOf(anchor, paidPeriods(user));
    }

    /** Índice (1..N) del período que contiene ese instante; N si la cadena ya terminó. Requiere que haya pagado. */
    public static int currentIndex(AppUser user, ZonedDateTime now) {
        ZonedDateTime anchor = anchor(user);
        int n = paidPeriods(user);
        for (int k = 1; k < n; k++) {
            if (now.isBefore(endOf(anchor, k))) {
                return k;
            }
        }
        return n;
    }

    public static boolean isExpired(AppUser user, ZonedDateTime now) {
        ZonedDateTime end = finalEnd(user);
        return end != null && !now.isBefore(end);
    }

    /** ¿Ya renovó por adelantado? Hay al menos un período pagado después del actual (y el plan no venció). */
    public static boolean hasQueued(AppUser user, ZonedDateTime now) {
        return user.getPaidAt() != null && !isExpired(user, now) && currentIndex(user, now) < paidPeriods(user);
    }

    /** Fin (exclusivo) del período actual. null si nunca pagó. */
    public static ZonedDateTime currentEnd(AppUser user, ZonedDateTime now) {
        ZonedDateTime anchor = anchor(user);
        return anchor == null ? null : endOf(anchor, currentIndex(user, now));
    }

    /** Inicio del período actual. null si nunca pagó. */
    public static ZonedDateTime currentStart(AppUser user, ZonedDateTime now) {
        ZonedDateTime anchor = anchor(user);
        return anchor == null ? null : startOf(anchor, currentIndex(user, now));
    }

    /**
     * Índice del período al que cuenta una clase de esa fecha: todo lo anterior al fin del período actual cuenta contra
     * el actual; después, el primero cuyo fin (en fecha de Chile) es posterior a la clase. Más allá del último pagado
     * devuelve N + 1 (fuera del plan; lo rechaza isWithinPaidPeriod).
     */
    public static int indexOfClassDate(AppUser user, LocalDate classDate, ZonedDateTime now) {
        ZonedDateTime anchor = anchor(user);
        int current = currentIndex(user, now);
        int n = paidPeriods(user);
        if (!classDate.isBefore(endOf(anchor, current).toLocalDate())) {
            for (int k = current + 1; k <= n; k++) {
                if (classDate.isBefore(endOf(anchor, k).toLocalDate())) {
                    return k;
                }
            }
            return n + 1;
        }
        return current;
    }

    /** ¿Un pago ahora, con ese plan, renueva por adelantado? Plan vigente (no vencido) y el MISMO plan que ya tiene. */
    public static boolean canExtend(AppUser user, Long planId, Instant now) {
        return planId != null
                && planId.equals(user.getPlanId())
                && user.getPaidAt() != null
                && !isExpired(user, now.atZone(GYM_ZONE));
    }

    /**
     * Registra un pago hecho en {@code now}. Con {@code allowEarly} y un plan vigente del mismo plan, alarga la cadena un
     * mes más (la cola se encadena desde SU vencimiento, sin tocar el período actual ni sus clases usadas). En cualquier
     * otro caso arranca una cadena nueva desde {@code now} y limpia lo que traía de una importación (usedSessionsAtImport
     * lo fija después quien llama si el admin declaró clases usadas).
     *
     * @return true si fue una renovación anticipada
     */
    public static boolean applyPayment(AppUser user, Long planId, Instant now, boolean allowEarly) {
        if (allowEarly && canExtend(user, planId, now)) {
            user.setPeriodStart(anchor(user).toInstant());
            user.setPaidPeriods(paidPeriods(user) + 1);
            user.setPaidAt(now);
            return true;
        }
        startFreshChain(user, planId, now);
        user.setUsedSessionsAtImport(null);
        return false;
    }

    /** Meses completos ya pagados por adelantado (en cola) que se perderían si un pago reiniciara la cadena ahora. */
    public static int queuedMonths(AppUser user, ZonedDateTime now) {
        return hasQueued(user, now) ? paidPeriods(user) - currentIndex(user, now) : 0;
    }

    /** Días enteros que le quedan del período actual (0 si nunca pagó o ya venció). Con cola, solo cuenta el actual. */
    public static long daysLeftInCurrent(AppUser user, ZonedDateTime now) {
        if (user.getPaidAt() == null || isExpired(user, now)) {
            return 0;
        }
        return Math.max(0, java.time.Duration.between(now, currentEnd(user, now)).toDays());
    }

    /** Cadena nueva de 1 mes que parte en {@code start} (pago de hoy o fecha elegida por el admin). */
    public static void startFreshChain(AppUser user, Long planId, Instant start) {
        user.setPlanId(planId);
        user.setPaidAt(start);
        user.setPeriodStart(null);
        user.setPaidPeriods(null);
    }

    /** Quita todo rastro de pago (revokePlan). */
    public static void clear(AppUser user) {
        user.setPlanId(null);
        user.setPaidAt(null);
        user.setPeriodStart(null);
        user.setPaidPeriods(null);
    }
}
