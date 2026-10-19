package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.GymPlan;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.MemberResponse;
import com.cortesdev.mygym.models.dto.MemberSummaryResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymPlanRepository;
import com.cortesdev.mygym.repositories.MemberFixedClassRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Renovación anticipada vista desde MemberService: estado, orden, cupos por período y DTO (Mockito, sin DB). Las reservas
 * son una lista de fechas en memoria; los conteos del repositorio se responden con ella, así el cupo por período se prueba
 * con las mismas ventanas de fechas que usa el código real.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EarlyRenewalMemberServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Santiago");
    private static final Long GYM_ID = 4L;
    private static final Long PLAN_ID = 5L;

    @Mock private AppUserRepository appUserRepository;
    @Mock private GymPlanRepository gymPlanRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private MemberFixedClassRepository memberFixedClassRepository;
    @InjectMocks private MemberService memberService;

    private final List<LocalDate> booked = new ArrayList<>();
    private GymPlan plan8;
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        plan8 = GymPlan.builder().id(PLAN_ID).gymId(GYM_ID).name("Plan 8").monthlyClasses(8).build();
        when(gymPlanRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan8));
        when(gymPlanRepository.findAllById(anyCollection())).thenAnswer(inv -> List.of(plan8));
        when(reservationRepository.countByMemberIdAndStatusAndClassDateBetween(
                        anyLong(), eq(ReservationStatus.BOOKED), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    LocalDate from = inv.getArgument(2);
                    LocalDate to = inv.getArgument(3);
                    return (int) booked.stream().filter(d -> !d.isBefore(from) && !d.isAfter(to)).count();
                });
        when(reservationRepository.findByMemberIdInAndStatusAndClassDateBetween(
                        anyList(), eq(ReservationStatus.BOOKED), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    List<Long> ids = inv.getArgument(0);
                    LocalDate from = inv.getArgument(2);
                    LocalDate to = inv.getArgument(3);
                    return booked.stream()
                            .filter(d -> !d.isBefore(from) && !d.isAfter(to))
                            .map(d -> Reservation.builder()
                                    .memberId(ids.get(0)).classDate(d).status(ReservationStatus.BOOKED).build())
                            .toList();
                });
    }

    private AppUser member(String name, ZonedDateTime paidAt) {
        return AppUser.builder()
                .id(nextId++).name(name).email(name + "@x.cl").role(Role.MEMBER).gymId(GYM_ID).active(true)
                .planId(PLAN_ID).paidAt(paidAt == null ? null : paidAt.toInstant()).build();
    }

    private static ZonedDateTime daysAgo(int days) {
        return ZonedDateTime.now(ZONE).minusDays(days);
    }

    /** Pagó hace `paidDaysAgo` días y renovó por adelantado ahora. */
    private AppUser renewedEarly(String name, int paidDaysAgo) {
        AppUser u = member(name, daysAgo(paidDaysAgo));
        assertThat(MembershipPeriods.applyPayment(u, PLAN_ID, Instant.now(), true)).isTrue();
        return u;
    }

    // ---- Estado

    @Test
    void sinCuePorVencerSigueDependiendoDelVencimientoActual() {
        AppUser u = member("a", daysAgo(29)); // le quedan ~1 a 2 días

        MemberResponse r = memberService.getOwnMembership(saved(u));

        assertThat(r.membershipStatus()).isEqualTo("EXPIRING_SOON");
        assertThat(r.canRenewEarly()).isTrue();
        assertThat(r.queuedPeriodEnd()).isNull();
    }

    @Test
    void conCuaYaNoEstaPorVencerPorqueElVencimientoQueManejaEsElFinal() {
        AppUser u = renewedEarly("a", 29); // el período actual vence en ~1-2 días, pero ya pagó el siguiente

        MemberResponse r = memberService.getOwnMembership(saved(u));

        assertThat(r.membershipStatus()).isEqualTo("ACTIVE");
        assertThat(memberService.hasActiveMembership(u)).isTrue();
    }

    @Test
    void conCuaElVencimientoQueVeElSocioEsElFinalYElActualVaAparte() {
        AppUser u = renewedEarly("a", 20);
        ZonedDateTime anchor = MembershipPeriods.anchor(u);

        MemberResponse r = memberService.getOwnMembership(saved(u));

        assertThat(r.planEndDate()).isEqualTo(anchor.plusMonths(2).toInstant());
        assertThat(r.queuedPeriodEnd()).isEqualTo(anchor.plusMonths(2).toInstant());
        assertThat(r.currentPeriodEnd()).isEqualTo(anchor.plusMonths(1).toInstant());
        // paidAt sigue siendo el instante real del pago (hoy), no el inicio de ningún período
        assertThat(r.paidAt()).isEqualTo(u.getPaidAt());
        assertThat(r.paidAt()).isAfter(anchor.toInstant());
    }

    @Test
    void canRenewEarlyEsFalsoConColaVencidoOSinPago() {
        AppUser queued = renewedEarly("q", 10);
        AppUser expired = member("e", daysAgo(90));
        AppUser unpaid = member("u", null);
        unpaid.setPlanId(null);

        MemberResponse rq = memberService.getOwnMembership(saved(queued));
        MemberResponse re = memberService.getOwnMembership(saved(expired));
        MemberResponse ru = memberService.getOwnMembership(saved(unpaid));

        assertThat(rq.canRenewEarly()).isFalse();
        assertThat(rq.earlyRenewalStartsOn()).isNull();
        assertThat(re.canRenewEarly()).isFalse();
        assertThat(re.membershipStatus()).isEqualTo("EXPIRED");
        assertThat(ru.canRenewEarly()).isFalse();
        assertThat(ru.membershipStatus()).isEqualTo("UNPAID");
        assertThat(ru.planEndDate()).isNull();
        assertThat(ru.currentPeriodEnd()).isNull();
    }

    @Test
    void renovarSoloSeOfreceEnLosUltimosTresDias() {
        AppUser lejos = member("l", daysAgo(10)); // le quedan ~20 días
        AppUser cerca = member("c", daysAgo(29)); // le quedan ~1-2 días

        assertThat(memberService.getOwnMembership(saved(lejos)).canRenewEarly()).isFalse();
        assertThat(memberService.getOwnMembership(saved(lejos)).earlyRenewalStartsOn()).isNull();
        assertThat(memberService.getOwnMembership(saved(cerca)).canRenewEarly()).isTrue();
        // El admin sí puede sumar el mes del mismo plan vigente en cualquier momento (no pierde días): canExtend no mira la ventana.
        assertThat(MembershipPeriods.canExtend(lejos, PLAN_ID, Instant.now())).isTrue();
    }

    @Test
    void earlyRenewalStartsOnEsElVencimientoActual() {
        AppUser u = member("a", daysAgo(29));

        MemberResponse r = memberService.getOwnMembership(saved(u));

        assertThat(r.canRenewEarly()).isTrue();
        assertThat(r.earlyRenewalStartsOn())
                .isEqualTo(MembershipPeriods.finalEnd(u).toLocalDate());
        assertThat(r.currentPeriodEnd()).isEqualTo(r.planEndDate());
    }

    @Test
    void conColaSePuedeReservarHastaElVencimientoFinal() {
        AppUser u = renewedEarly("a", 10);
        LocalDate lastDay = MembershipPeriods.finalEnd(u).toLocalDate().minusDays(1);

        assertThat(MemberService.paidUntil(u)).isEqualTo(lastDay);
        assertThat(MemberService.isWithinPaidPeriod(u, lastDay)).isTrue();
        assertThat(MemberService.isWithinPaidPeriod(u, lastDay.plusDays(1))).isFalse();
    }

    // ---- Cupos

    @Test
    void elCupoDelPeriodoActualQuedaIntactoAlPagarPorAdelantado() {
        AppUser u = member("a", daysAgo(10));
        LocalDate today = LocalDate.now(ZONE);
        booked.add(today.plusDays(1));
        booked.add(today.plusDays(2));
        booked.add(today.plusDays(3));
        Integer before = memberService.remainingClasses(u);

        MembershipPeriods.applyPayment(u, PLAN_ID, Instant.now(), true);

        assertThat(before).isEqualTo(5);
        assertThat(memberService.remainingClasses(u)).isEqualTo(5); // ni regalado ni consumido
    }

    @Test
    void elCupoNuevoEsCompletoYParteEnCero() {
        AppUser u = renewedEarly("a", 10);
        LocalDate curEnd = MembershipPeriods.currentEnd(u, ZonedDateTime.now(ZONE)).toLocalDate();
        // 3 clases del período actual ya reservadas: no pueden descontar del nuevo
        booked.add(curEnd.minusDays(1));
        booked.add(curEnd.minusDays(2));
        booked.add(curEnd.minusDays(3));

        assertThat(memberService.remainingClassesInQueuedPeriod(u)).isEqualTo(8);
        assertThat(memberService.remainingClasses(u)).isEqualTo(5);
    }

    @Test
    void unaClaseDespuesDelVencimientoActualGastaElCupoNuevoYUnaAnteriorElActual() {
        AppUser u = renewedEarly("a", 10);
        LocalDate curEnd = MembershipPeriods.currentEnd(u, ZonedDateTime.now(ZONE)).toLocalDate();
        booked.add(curEnd.minusDays(1)); // período actual
        booked.add(curEnd); // primer día del período nuevo
        booked.add(curEnd.plusDays(5)); // período nuevo

        assertThat(memberService.remainingClasses(u)).isEqualTo(7);
        assertThat(memberService.remainingClasses(u, curEnd.minusDays(1))).isEqualTo(7);
        assertThat(memberService.remainingClassesInQueuedPeriod(u)).isEqualTo(6);
        assertThat(memberService.remainingClasses(u, curEnd)).isEqualTo(6);
        assertThat(memberService.remainingClasses(u, curEnd.plusDays(5))).isEqualTo(6);
    }

    @Test
    void agotarElCupoActualNoBloqueaReservarEnElPeriodoNuevo() {
        AppUser u = renewedEarly("a", 10);
        LocalDate curEnd = MembershipPeriods.currentEnd(u, ZonedDateTime.now(ZONE)).toLocalDate();
        for (int i = 1; i <= 8; i++) {
            booked.add(curEnd.minusDays(i));
        }

        assertThat(memberService.hasQuotaAvailable(u)).isFalse();
        assertThat(memberService.hasQuotaAvailable(u, curEnd.minusDays(1))).isFalse();
        assertThat(memberService.hasQuotaAvailable(u, curEnd)).isTrue();
    }

    @Test
    void agotarElCupoNuevoNoBloqueaElActual() {
        AppUser u = renewedEarly("a", 10);
        LocalDate curEnd = MembershipPeriods.currentEnd(u, ZonedDateTime.now(ZONE)).toLocalDate();
        for (int i = 0; i < 8; i++) {
            booked.add(curEnd.plusDays(i));
        }

        assertThat(memberService.hasQuotaAvailable(u, curEnd)).isFalse();
        assertThat(memberService.hasQuotaAvailable(u, curEnd.minusDays(1))).isTrue();
        assertThat(memberService.hasQuotaAvailable(u)).isTrue();
    }

    @Test
    void unaFechaFueraDeLoPagadoNoTieneCupoQueGastarYLaRechazaLaValidacionDeFecha() {
        AppUser u = renewedEarly("a", 10);
        LocalDate beyond = MembershipPeriods.finalEnd(u).toLocalDate();

        assertThat(memberService.remainingClasses(u, beyond)).isNull();
        assertThat(MemberService.isWithinPaidPeriod(u, beyond)).isFalse();
    }

    @Test
    void unPlanLibreNoTieneTopeEnNingunPeriodo() {
        plan8.setMonthlyClasses(null);
        AppUser u = renewedEarly("a", 10);
        LocalDate curEnd = MembershipPeriods.currentEnd(u, ZonedDateTime.now(ZONE)).toLocalDate();
        for (int i = 0; i < 30; i++) {
            booked.add(curEnd.plusDays(i % 20));
        }

        assertThat(memberService.remainingClasses(u)).isNull();
        assertThat(memberService.remainingClasses(u, curEnd.plusDays(3))).isNull();
        assertThat(memberService.remainingClassesInQueuedPeriod(u)).isNull();
        assertThat(memberService.hasQuotaAvailable(u, curEnd.plusDays(3))).isTrue();
    }

    @Test
    void lasClasesYaUsadasDeUnaImportacionValenSoloParaElPrimerPeriodo() {
        AppUser u = member("a", daysAgo(10));
        u.setUsedSessionsAtImport(3);
        MembershipPeriods.applyPayment(u, PLAN_ID, Instant.now(), true);
        LocalDate curEnd = MembershipPeriods.currentEnd(u, ZonedDateTime.now(ZONE)).toLocalDate();

        assertThat(memberService.remainingClasses(u)).isEqualTo(5);
        assertThat(memberService.remainingClassesInQueuedPeriod(u)).isEqualTo(8);
        assertThat(memberService.remainingClasses(u, curEnd)).isEqualTo(8);
    }

    @Test
    void alPasarAlPeriodoNuevoEsteSeVuelveElActualConSuCupo() {
        // pagó hace 40 días y renovó por adelantado cuando le quedaban 5: hoy ya está en el segundo período
        AppUser u = member("a", daysAgo(35));
        u.setPeriodStart(daysAgo(35).toInstant());
        u.setPaidPeriods(2);
        u.setPaidAt(daysAgo(30).toInstant());
        LocalDate periodTwoStart = MembershipPeriods.currentStart(u, ZonedDateTime.now(ZONE)).toLocalDate();
        booked.add(periodTwoStart.plusDays(1));
        booked.add(periodTwoStart.minusDays(2)); // del período 1: no cuenta

        assertThat(MembershipPeriods.currentIndex(u, ZonedDateTime.now(ZONE))).isEqualTo(2);
        assertThat(memberService.remainingClasses(u)).isEqualTo(7);
        assertThat(memberService.remainingClassesInQueuedPeriod(u)).isNull(); // ya no hay cola
        assertThat(memberService.queuedPeriod(u)).isNull();
        MemberResponse r = memberService.getOwnMembership(saved(u));
        assertThat(r.sessionsRemaining()).isEqualTo(7);
        assertThat(r.canRenewEarly()).isFalse(); // su vencimiento final está a ~25 días: fuera de la ventana de 3 días
    }

    @Test
    void elDtoTrae_elCupoRestanteDeCadaPeriodo() {
        AppUser u = renewedEarly("a", 10);
        LocalDate curEnd = MembershipPeriods.currentEnd(u, ZonedDateTime.now(ZONE)).toLocalDate();
        booked.add(curEnd.minusDays(1));
        booked.add(curEnd.minusDays(2));
        booked.add(curEnd.plusDays(1));

        MemberResponse r = memberService.getOwnMembership(saved(u));

        assertThat(r.monthlyClasses()).isEqualTo(8);
        assertThat(r.sessionsRemaining()).isEqualTo(6);
        assertThat(r.queuedSessionsRemaining()).isEqualTo(7);
    }

    @Test
    void currentPeriodYQueuedPeriodDelimitanLosRangosDeFechas() {
        AppUser u = renewedEarly("a", 10);
        ZonedDateTime anchor = MembershipPeriods.anchor(u);

        MemberService.PeriodRange current = memberService.currentPeriod(u);
        MemberService.PeriodRange queued = memberService.queuedPeriod(u);

        assertThat(current.start()).isEqualTo(anchor.toLocalDate());
        assertThat(current.endInclusive()).isEqualTo(anchor.plusMonths(1).toLocalDate().minusDays(1));
        assertThat(queued.start()).isEqualTo(anchor.plusMonths(1).toLocalDate());
        assertThat(queued.endInclusive()).isEqualTo(anchor.plusMonths(2).toLocalDate().minusDays(1));
        assertThat(memberService.queuedPeriod(member("n", daysAgo(5)))).isNull();
    }

    // ---- Búsqueda, filtros, orden y conteos

    private Long saved(AppUser u) {
        when(appUserRepository.findById(u.getId())).thenReturn(Optional.of(u));
        return u.getId();
    }

    @Test
    void elFiltroPorVencerNoIncluyeAQuienYaRenovoPorAdelantado() {
        AppUser expiring = member("por-vencer", daysAgo(29));
        AppUser renewed = renewedEarly("renovado", 29); // mismo vencimiento actual, pero ya pagó el siguiente
        AppUser active = member("activo", daysAgo(5));
        when(appUserRepository.findByGymIdAndRole(GYM_ID, Role.MEMBER)).thenReturn(List.of(expiring, renewed, active));

        var expiringSoon = memberService.searchMembers(GYM_ID, null, "EXPIRING_SOON", null, 0, 10);
        var activeOnes = memberService.searchMembers(GYM_ID, null, "ACTIVE", null, 0, 10);

        assertThat(expiringSoon.items()).extracting(MemberResponse::name).containsExactly("por-vencer");
        assertThat(activeOnes.items()).extracting(MemberResponse::name).containsExactlyInAnyOrder("renovado", "activo");
    }

    @Test
    void elOrdenPorVencimientoUsaElVencimientoFinal() {
        // Por pago real el orden sería c, a, b; por vencimiento FINAL a (cadena de 2 meses desde hace 100 días, venció hace ~40) va primero.
        AppUser vencidoHaceMucho = member("c", daysAgo(60));
        AppUser vencidoReciente = member("b", daysAgo(35));
        AppUser conColaVencida = member("a", daysAgo(100));
        conColaVencida.setPeriodStart(daysAgo(100).toInstant()); // 2 meses seguidos desde hace 100 días
        conColaVencida.setPaidPeriods(2);
        conColaVencida.setPaidAt(daysAgo(50).toInstant()); // el pago real es más reciente que el de c
        when(appUserRepository.findByGymIdAndRole(GYM_ID, Role.MEMBER))
                .thenReturn(List.of(vencidoReciente, conColaVencida, vencidoHaceMucho));

        var expired = memberService.searchMembers(GYM_ID, null, "EXPIRED", null, 0, 10);

        assertThat(expired.items()).extracting(MemberResponse::name).containsExactly("a", "c", "b");
    }

    @Test
    void elResumenCuentaPorVencimientoFinal() {
        AppUser expiring = member("e", daysAgo(29));
        AppUser renewed = renewedEarly("r", 29);
        AppUser expired = member("x", daysAgo(90));
        AppUser unpaid = member("u", null);
        when(appUserRepository.findByGymIdAndRole(GYM_ID, Role.MEMBER)).thenReturn(List.of(expiring, renewed, expired, unpaid));

        MemberSummaryResponse s = memberService.memberSummary(GYM_ID);

        assertThat(s.expiringSoon()).isEqualTo(1);
        assertThat(s.active()).isEqualTo(1);
        assertThat(s.expired()).isEqualTo(1);
        assertThat(s.unpaid()).isEqualTo(1);
    }
}
