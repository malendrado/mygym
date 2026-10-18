package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.cortesdev.mygym.models.AppUser;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

/** Cuentas de período de la renovación anticipada (puras, sin DB ni Spring). */
class MembershipPeriodsTest {

    private static final ZoneId ZONE = ZoneId.of("America/Santiago");
    private static final Long PLAN = 5L;

    private static ZonedDateTime at(int y, int m, int d, int h) {
        return ZonedDateTime.of(y, m, d, h, 0, 0, 0, ZONE);
    }

    private static AppUser paid(ZonedDateTime paidAt) {
        return AppUser.builder().id(1L).planId(PLAN).paidAt(paidAt.toInstant()).build();
    }

    @Test
    void unaCadenaSinColumnasNuevasSeComportaComoAntesUnMesDesdeElPago() {
        AppUser u = paid(at(2027, 3, 10, 15));

        assertThat(MembershipPeriods.finalEnd(u)).isEqualTo(at(2027, 4, 10, 15));
        assertThat(MembershipPeriods.currentIndex(u, at(2027, 3, 20, 9))).isEqualTo(1);
        assertThat(MembershipPeriods.hasQueued(u, at(2027, 3, 20, 9))).isFalse();
        assertThat(MembershipPeriods.isExpired(u, at(2027, 4, 10, 15))).isTrue();
        assertThat(MembershipPeriods.isExpired(u, at(2027, 4, 10, 14))).isFalse();
    }

    @Test
    void sinPagoNoHayPeriodoNiVencimiento() {
        AppUser u = AppUser.builder().id(1L).build();

        assertThat(MembershipPeriods.finalEnd(u)).isNull();
        assertThat(MembershipPeriods.anchor(u)).isNull();
        assertThat(MembershipPeriods.hasQueued(u, at(2027, 3, 1, 9))).isFalse();
        assertThat(MembershipPeriods.isExpired(u, at(2027, 3, 1, 9))).isFalse();
    }

    @Test
    void pagarPorAdelantadoConservaLosDiasYElPeriodoNuevoParteEnElVencimientoActual() {
        // pagó el 1 de marzo; el 20 de marzo renueva: el período nuevo parte el 1 de abril, no el 20 de marzo.
        AppUser u = paid(at(2027, 3, 1, 10));
        Instant renewal = at(2027, 3, 20, 12).toInstant();

        boolean early = MembershipPeriods.applyPayment(u, PLAN, renewal, true);

        assertThat(early).isTrue();
        assertThat(u.getPaidAt()).isEqualTo(renewal); // el pago real queda como pago real
        assertThat(MembershipPeriods.anchor(u)).isEqualTo(at(2027, 3, 1, 10));
        assertThat(MembershipPeriods.currentEnd(u, at(2027, 3, 20, 12))).isEqualTo(at(2027, 4, 1, 10));
        assertThat(MembershipPeriods.finalEnd(u)).isEqualTo(at(2027, 5, 1, 10));
        assertThat(MembershipPeriods.hasQueued(u, at(2027, 3, 20, 12))).isTrue();
    }

    @Test
    void elPeriodoNuevoPasaAserElActualAlVencerElAnterior() {
        AppUser u = paid(at(2027, 3, 1, 10));
        MembershipPeriods.applyPayment(u, PLAN, at(2027, 3, 20, 12).toInstant(), true);

        ZonedDateTime inQueued = at(2027, 4, 15, 9);

        assertThat(MembershipPeriods.currentIndex(u, inQueued)).isEqualTo(2);
        assertThat(MembershipPeriods.currentStart(u, inQueued)).isEqualTo(at(2027, 4, 1, 10));
        assertThat(MembershipPeriods.hasQueued(u, inQueued)).isFalse(); // ya no hay nada por delante
        assertThat(MembershipPeriods.isExpired(u, at(2027, 5, 1, 10))).isTrue();
    }

    @Test
    void finDeMesNoDerivaEncadenandoTresMeses() {
        // 31 ene -> 28 feb (o 29 en bisiesto) -> 31 mar: cada borde se calcula desde el ancla, nunca sobre un borde ajustado.
        for (int year : new int[] {2027, 2028}) {
            AppUser u = paid(at(year, 1, 31, 10));
            Instant firstRenewal = at(year, 2, 2, 10).toInstant();
            Instant secondRenewal = at(year, 2, 3, 10).toInstant();

            MembershipPeriods.applyPayment(u, PLAN, firstRenewal, true);
            MembershipPeriods.applyPayment(u, PLAN, secondRenewal, true);

            ZonedDateTime anchor = MembershipPeriods.anchor(u);
            int febLastDay = year == 2028 ? 29 : 28;
            assertThat(MembershipPeriods.endOf(anchor, 1)).isEqualTo(at(year, 2, febLastDay, 10));
            assertThat(MembershipPeriods.endOf(anchor, 2)).isEqualTo(at(year, 3, 31, 10)); // sin deriva (no 28/29 de marzo)
            assertThat(MembershipPeriods.endOf(anchor, 3)).isEqualTo(at(year, 4, 30, 10));
            assertThat(MembershipPeriods.finalEnd(u)).isEqualTo(at(year, 4, 30, 10));
            assertThat(MembershipPeriods.paidPeriods(u)).isEqualTo(3);
        }
    }

    @Test
    void unSegundoAnticipadoSeEncadenaDesdeElVencimientoDeLaCola() {
        AppUser u = paid(at(2027, 3, 1, 10));
        MembershipPeriods.applyPayment(u, PLAN, at(2027, 3, 10, 10).toInstant(), true);
        MembershipPeriods.applyPayment(u, PLAN, at(2027, 3, 11, 10).toInstant(), true);

        assertThat(MembershipPeriods.finalEnd(u)).isEqualTo(at(2027, 6, 1, 10));
        // El período actual sigue siendo el primero: lo ya pagado no se mueve.
        assertThat(MembershipPeriods.currentEnd(u, at(2027, 3, 12, 9))).isEqualTo(at(2027, 4, 1, 10));
        assertThat(MembershipPeriods.hasQueued(u, at(2027, 3, 12, 9))).isTrue();
    }

    @Test
    void conPlanVencidoElPagoArrancaCadenaNuevaDesdeAhora() {
        AppUser u = paid(at(2027, 1, 1, 10));
        u.setUsedSessionsAtImport(3);
        Instant now = at(2027, 3, 20, 12).toInstant(); // venció el 1 de febrero

        boolean early = MembershipPeriods.applyPayment(u, PLAN, now, true);

        assertThat(early).isFalse();
        assertThat(u.getPaidAt()).isEqualTo(now);
        assertThat(u.getPeriodStart()).isNull();
        assertThat(u.getPaidPeriods()).isNull();
        assertThat(u.getUsedSessionsAtImport()).isNull();
        assertThat(MembershipPeriods.finalEnd(u)).isEqualTo(at(2027, 4, 20, 12));
    }

    @Test
    void conUnaCadenaYaVencidaElPagoNoAprovechaElAncla() {
        AppUser u = paid(at(2027, 1, 1, 10));
        MembershipPeriods.applyPayment(u, PLAN, at(2027, 1, 5, 10).toInstant(), true); // vence el 1 de marzo
        Instant now = at(2027, 6, 1, 9).toInstant();

        boolean early = MembershipPeriods.applyPayment(u, PLAN, now, true);

        assertThat(early).isFalse();
        assertThat(MembershipPeriods.anchor(u)).isEqualTo(at(2027, 6, 1, 9));
        assertThat(MembershipPeriods.finalEnd(u)).isEqualTo(at(2027, 7, 1, 9));
    }

    @Test
    void nuncaPagoArrancaDesdeAhora() {
        AppUser u = AppUser.builder().id(1L).build();
        Instant now = at(2027, 3, 20, 12).toInstant();

        boolean early = MembershipPeriods.applyPayment(u, PLAN, now, true);

        assertThat(early).isFalse();
        assertThat(u.getPlanId()).isEqualTo(PLAN);
        assertThat(u.getPaidAt()).isEqualTo(now);
        assertThat(MembershipPeriods.finalEnd(u)).isEqualTo(at(2027, 4, 20, 12));
    }

    @Test
    void cambiarDePlanNoEsRenovacionAnticipada() {
        AppUser u = paid(at(2027, 3, 1, 10));
        Instant now = at(2027, 3, 10, 10).toInstant();

        boolean early = MembershipPeriods.applyPayment(u, 99L, now, true);

        assertThat(early).isFalse();
        assertThat(u.getPlanId()).isEqualTo(99L);
        assertThat(u.getPaidAt()).isEqualTo(now);
        assertThat(MembershipPeriods.finalEnd(u)).isEqualTo(at(2027, 4, 10, 10));
    }

    @Test
    void conAllowEarlyEnFalsoSiempreArrancaCadenaNueva() {
        AppUser u = paid(at(2027, 3, 1, 10));
        Instant now = at(2027, 3, 10, 10).toInstant();

        boolean early = MembershipPeriods.applyPayment(u, PLAN, now, false);

        assertThat(early).isFalse();
        assertThat(MembershipPeriods.finalEnd(u)).isEqualTo(at(2027, 4, 10, 10));
    }

    @Test
    void laRenovacionAnticipadaNoTocaLoQueTraiaDeUnaImportacion() {
        AppUser u = paid(at(2027, 3, 1, 10));
        u.setUsedSessionsAtImport(3);

        MembershipPeriods.applyPayment(u, PLAN, at(2027, 3, 10, 10).toInstant(), true);

        assertThat(u.getUsedSessionsAtImport()).isEqualTo(3); // sigue valiendo para el período actual
    }

    @Test
    void cadaClaseCuentaParaElPeriodoQueContieneSuFecha() {
        AppUser u = paid(at(2027, 3, 1, 10));
        MembershipPeriods.applyPayment(u, PLAN, at(2027, 3, 10, 10).toInstant(), true);
        ZonedDateTime now = at(2027, 3, 12, 9);

        assertThat(MembershipPeriods.indexOfClassDate(u, LocalDate.of(2027, 3, 12), now)).isEqualTo(1);
        assertThat(MembershipPeriods.indexOfClassDate(u, LocalDate.of(2027, 3, 31), now)).isEqualTo(1);
        // el 1 de abril (fecha de vencimiento actual) ya es del período nuevo
        assertThat(MembershipPeriods.indexOfClassDate(u, LocalDate.of(2027, 4, 1), now)).isEqualTo(2);
        assertThat(MembershipPeriods.indexOfClassDate(u, LocalDate.of(2027, 4, 30), now)).isEqualTo(2);
        // el 1 de mayo ya está fuera de lo pagado
        assertThat(MembershipPeriods.indexOfClassDate(u, LocalDate.of(2027, 5, 1), now)).isEqualTo(3);
    }

    @Test
    void canExtendExigeElMismoPlanYQueNoHayaVencido() {
        AppUser u = paid(at(2027, 3, 1, 10));

        assertThat(MembershipPeriods.canExtend(u, PLAN, at(2027, 3, 20, 9).toInstant())).isTrue();
        assertThat(MembershipPeriods.canExtend(u, 7L, at(2027, 3, 20, 9).toInstant())).isFalse();
        assertThat(MembershipPeriods.canExtend(u, PLAN, at(2027, 4, 1, 10).toInstant())).isFalse();
        assertThat(MembershipPeriods.canExtend(u, null, at(2027, 3, 20, 9).toInstant())).isFalse();
    }

    @Test
    void clearLimpiaPlanPagoYCadena() {
        AppUser u = paid(at(2027, 3, 1, 10));
        MembershipPeriods.applyPayment(u, PLAN, at(2027, 3, 10, 10).toInstant(), true);

        MembershipPeriods.clear(u);

        assertThat(u.getPlanId()).isNull();
        assertThat(u.getPaidAt()).isNull();
        assertThat(u.getPeriodStart()).isNull();
        assertThat(u.getPaidPeriods()).isNull();
        assertThat(MembershipPeriods.finalEnd(u)).isNull();
    }

    // ---- Calendario: bisiesto, cambio de hora y borde por instante vs por fecha.

    @Test
    void anclaDel29DeFebreroBisiestoCaeEl28EnAnoComunYVuelveAlAncla() {
        AppUser u = paid(at(2028, 2, 29, 10));
        u.setPaidPeriods(13);
        ZonedDateTime anchor = MembershipPeriods.anchor(u);

        assertThat(MembershipPeriods.endOf(anchor, 1)).isEqualTo(at(2028, 3, 29, 10));
        assertThat(MembershipPeriods.endOf(anchor, 12)).isEqualTo(at(2029, 2, 28, 10)); // 2029 no es bisiesto
        assertThat(MembershipPeriods.endOf(anchor, 13)).isEqualTo(at(2029, 3, 29, 10)); // sin deriva al 28
        assertThat(MembershipPeriods.endOf(anchor, 48)).isEqualTo(at(2032, 2, 29, 10)); // 2032 vuelve a ser bisiesto
    }

    @Test
    void elVencimientoDe28DeFebreroAlRenovarSeEncadenaSinPerderElDiaBisiesto() {
        AppUser u = paid(at(2028, 1, 29, 10));
        MembershipPeriods.applyPayment(u, PLAN, at(2028, 2, 10, 10).toInstant(), true);

        assertThat(MembershipPeriods.endOf(MembershipPeriods.anchor(u), 1)).isEqualTo(at(2028, 2, 29, 10));
        assertThat(MembershipPeriods.finalEnd(u)).isEqualTo(at(2028, 3, 29, 10));
    }

    @Test
    void elCambioDeHoraDeSantiagoConservaLaHoraDeLaPared() {
        // Chile pasa de horario de verano (-03) a invierno (-04) en abril: el borde sigue a las 10:00 de la pared, aunque
        // entre ambos instantes haya 30 días + 1 hora.
        AppUser u = paid(at(2027, 3, 20, 10));
        u.setPaidPeriods(3);
        ZonedDateTime anchor = MembershipPeriods.anchor(u);

        ZonedDateTime end1 = MembershipPeriods.endOf(anchor, 1);
        ZonedDateTime end3 = MembershipPeriods.endOf(anchor, 3);

        assertThat(anchor.getOffset()).isNotEqualTo(end1.getOffset()); // sí cruzó el cambio de hora
        assertThat(end1).isEqualTo(at(2027, 4, 20, 10));
        assertThat(end1.getHour()).isEqualTo(10);
        assertThat(end3).isEqualTo(at(2027, 6, 20, 10));
        assertThat(java.time.Duration.between(anchor, end1)).isEqualTo(java.time.Duration.ofDays(31).plusHours(1));
        // y el estado se decide por el instante real
        assertThat(MembershipPeriods.isExpired(u, end3.minusSeconds(1))).isFalse();
        assertThat(MembershipPeriods.isExpired(u, end3)).isTrue();
    }

    @Test
    void elCambioDeHoraDeSeptiembreTambienConservaLaHoraDeLaPared() {
        AppUser u = paid(at(2027, 8, 20, 10));
        u.setPaidPeriods(2);
        ZonedDateTime anchor = MembershipPeriods.anchor(u);

        ZonedDateTime end1 = MembershipPeriods.endOf(anchor, 1);

        assertThat(anchor.getOffset()).isNotEqualTo(end1.getOffset()); // invierno -> verano
        assertThat(end1).isEqualTo(at(2027, 9, 20, 10));
        assertThat(MembershipPeriods.endOf(anchor, 2)).isEqualTo(at(2027, 10, 20, 10));
    }

    @Test
    void elDiaDelBordeElPeriodoActualPorInstanteNoCoincideConElDeLaFechaDeLaClase() {
        // vence el 28/02 a las 15:00; a las 00:30 de ese día el actual (por instante) sigue siendo el 1...
        AppUser u = paid(at(2027, 1, 28, 15));
        MembershipPeriods.applyPayment(u, PLAN, at(2027, 2, 10, 9).toInstant(), true);
        ZonedDateTime edgeDay = at(2027, 2, 28, 0).plusMinutes(30);

        assertThat(MembershipPeriods.currentIndex(u, edgeDay)).isEqualTo(1);
        assertThat(MembershipPeriods.hasQueued(u, edgeDay)).isTrue();
        // ...pero una clase de HOY (28/02) ya cuenta contra el período nuevo, y la de ayer contra el actual.
        assertThat(MembershipPeriods.indexOfClassDate(u, LocalDate.of(2027, 2, 27), edgeDay)).isEqualTo(1);
        assertThat(MembershipPeriods.indexOfClassDate(u, LocalDate.of(2027, 2, 28), edgeDay)).isEqualTo(2);
        // pasada la hora de vencimiento el actual también es el 2 por instante: se alinean
        ZonedDateTime afterEdge = at(2027, 2, 28, 16);
        assertThat(MembershipPeriods.currentIndex(u, afterEdge)).isEqualTo(2);
        assertThat(MembershipPeriods.indexOfClassDate(u, LocalDate.of(2027, 2, 28), afterEdge)).isEqualTo(2);
        // más allá de lo pagado: N + 1
        assertThat(MembershipPeriods.indexOfClassDate(u, LocalDate.of(2027, 3, 28), edgeDay)).isEqualTo(3);
    }

    @Test
    void mesesEnColaYDiasRestantesParaLaVistaPrevia() {
        AppUser u = paid(at(2027, 3, 1, 10));
        MembershipPeriods.applyPayment(u, PLAN, at(2027, 3, 5, 10).toInstant(), true);
        MembershipPeriods.applyPayment(u, PLAN, at(2027, 3, 6, 10).toInstant(), true);
        ZonedDateTime now = at(2027, 3, 21, 10);

        assertThat(MembershipPeriods.queuedMonths(u, now)).isEqualTo(2);
        assertThat(MembershipPeriods.daysLeftInCurrent(u, now)).isEqualTo(11);
        // dentro del último período ya no queda cola
        assertThat(MembershipPeriods.queuedMonths(u, at(2027, 5, 10, 10))).isZero();
        // vencido o sin pago: nada que perder
        assertThat(MembershipPeriods.queuedMonths(u, at(2027, 6, 2, 10))).isZero();
        assertThat(MembershipPeriods.daysLeftInCurrent(u, at(2027, 6, 2, 10))).isZero();
        assertThat(MembershipPeriods.daysLeftInCurrent(AppUser.builder().id(2L).build(), now)).isZero();
    }
}
