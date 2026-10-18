package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymPlan;
import com.cortesdev.mygym.models.ManualPayment;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.PaymentPreviewResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymPlanRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ManualPaymentRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.services.exception.PaymentConflictException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Cola de meses pagados por adelantado frente a operaciones que reinician la cadena (cambio de plan, fecha explícita),
 * idempotencia del pago manual y revokePlan con reservas en el período encolado (Mockito, sin DB).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GymServiceQueueLossTest {

    private static final ZoneId ZONE = ZoneId.of("America/Santiago");
    private static final Long GYM_ID = 4L;
    private static final Long MEMBER_ID = 8L;
    private static final Long PLAN_ID = 12L;
    private static final Long OTHER_PLAN_ID = 13L;
    private static final Long ADMIN_ID = 99L;

    @Mock private GymRepository gymRepository;
    @Mock private GymPlanRepository gymPlanRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private ManualPaymentRepository manualPaymentRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private MemberLifecycleEmailService memberLifecycleEmailService;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock private MemberChangeNotifier memberChangeNotifier;
    @InjectMocks private GymService gymService;

    private final LocalDate today = LocalDate.now(ZONE);
    private Gym gym;
    private AppUser member;

    @BeforeEach
    void setUp() {
        gym = Gym.builder().id(GYM_ID).name("XaloGym").build();
        member = AppUser.builder().id(MEMBER_ID).gymId(GYM_ID).role(Role.MEMBER).name("Camila").build();
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(gym));
        when(gymRepository.existsById(GYM_ID)).thenReturn(true);
        when(appUserRepository.findByIdAndGymId(MEMBER_ID, GYM_ID)).thenReturn(Optional.of(member));
        when(appUserRepository.findLockedByIdAndGymId(MEMBER_ID, GYM_ID)).thenReturn(Optional.of(member));
        when(appUserRepository.findByGymIdAndRole(GYM_ID, Role.GYM_ADMIN)).thenReturn(List.of());
        plan(PLAN_ID, 8);
        plan(OTHER_PLAN_ID, 12);
    }

    private void plan(Long id, Integer monthlyClasses) {
        when(gymPlanRepository.findByIdAndGymId(id, GYM_ID))
                .thenReturn(Optional.of(GymPlan.builder()
                        .id(id).gymId(GYM_ID).name("Plan " + id).priceClp(35_000).monthlyClasses(monthlyClasses).build()));
    }

    /** Pagó hace 20 días y renovó por adelantado `queued` veces: quedan `queued` meses en cola. */
    private void withQueuedMonths(int queued) {
        member.setPlanId(PLAN_ID);
        member.setPaidAt(ZonedDateTime.now(ZONE).minusDays(20).toInstant());
        for (int i = 0; i < queued; i++) {
            MembershipPeriods.applyPayment(member, PLAN_ID, Instant.now(), true);
        }
        assertThat(MembershipPeriods.queuedMonths(member, ZonedDateTime.now(ZONE))).isEqualTo(queued);
    }

    private void pay(Long planId, String bank, LocalDate start, boolean confirm) {
        gymService.simulatePlanPayment(GYM_ID, MEMBER_ID, planId, bank, ADMIN_ID, start, null, confirm);
    }

    // ---- vista previa

    @Test
    void laVistaPreviaAvisaLosMesesEnColaYLosDiasQueSePierdenAlCambiarDePlan() {
        withQueuedMonths(2);

        PaymentPreviewResponse preview = gymService.previewPlanPayment(GYM_ID, MEMBER_ID, OTHER_PLAN_ID, null, null);

        assertThat(preview.earlyRenewal()).isFalse();
        assertThat(preview.lostPaidMonths()).isEqualTo(2);
        assertThat(preview.lostDays()).isBetween(8L, 11L); // alrededor de 10 días: un mes menos 20 transcurridos
    }

    @Test
    void laVistaPreviaConFechaExplicitaTambienAvisaLaPerdida() {
        withQueuedMonths(1);

        PaymentPreviewResponse preview = gymService.previewPlanPayment(GYM_ID, MEMBER_ID, PLAN_ID, today.minusDays(3), null);

        assertThat(preview.earlyRenewal()).isFalse();
        assertThat(preview.lostPaidMonths()).isEqualTo(1);
        assertThat(preview.lostDays()).isPositive();
    }

    @Test
    void sinColaSoloSePierdenLosDiasDelPeriodoActual() {
        withQueuedMonths(0);

        PaymentPreviewResponse preview = gymService.previewPlanPayment(GYM_ID, MEMBER_ID, OTHER_PLAN_ID, null, null);

        assertThat(preview.lostPaidMonths()).isZero();
        assertThat(preview.lostDays()).isPositive();
    }

    @Test
    void renovarPorAdelantadoOPagarConPlanVencidoNoPierdeNada() {
        withQueuedMonths(1);
        PaymentPreviewResponse early = gymService.previewPlanPayment(GYM_ID, MEMBER_ID, PLAN_ID, null, null);
        assertThat(early.earlyRenewal()).isTrue();
        assertThat(early.lostPaidMonths()).isZero();
        assertThat(early.lostDays()).isZero();

        member.setPaidAt(ZonedDateTime.now(ZONE).minusDays(90).toInstant());
        member.setPeriodStart(null);
        member.setPaidPeriods(null);
        PaymentPreviewResponse expired = gymService.previewPlanPayment(GYM_ID, MEMBER_ID, OTHER_PLAN_ID, null, null);
        assertThat(expired.lostPaidMonths()).isZero();
        assertThat(expired.lostDays()).isZero();
    }

    // ---- confirmación explícita del pago manual

    @Test
    void cambiarDePlanConDosMesesEncoladosSinConfirmarResponde409YNoTocaNada() {
        withQueuedMonths(2);
        Integer periodsBefore = member.getPaidPeriods();
        Instant paidAtBefore = member.getPaidAt();

        assertThatThrownBy(() -> pay(OTHER_PLAN_ID, "Banco Estado", null, false))
                .isInstanceOfSatisfying(PaymentConflictException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(PaymentConflictException.CODE_LOSE_QUEUED_MONTHS);
                    assertThat(e.getMessage())
                            .isEqualTo("Este cambio descarta 2 meses ya pagados por adelantado. Confirma para continuar.");
                });

        assertThat(member.getPaidPeriods()).isEqualTo(periodsBefore);
        assertThat(member.getPaidAt()).isEqualTo(paidAtBefore);
        assertThat(member.getPlanId()).isEqualTo(PLAN_ID);
        verify(appUserRepository, never()).save(any(AppUser.class));
        verify(manualPaymentRepository, never()).save(any(ManualPayment.class));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void conUnSoloMesEncoladoElMensajeVaEnSingular() {
        withQueuedMonths(1);

        assertThatThrownBy(() -> pay(OTHER_PLAN_ID, "Banco Estado", null, false))
                .isInstanceOf(PaymentConflictException.class)
                .hasMessage("Este cambio descarta 1 mes ya pagado por adelantado. Confirma para continuar.");
    }

    @Test
    void conLaConfirmacionElCambioDePlanProcedeYReiniciaLaCadena() {
        withQueuedMonths(2);

        pay(OTHER_PLAN_ID, "Banco Estado", null, true);

        assertThat(member.getPlanId()).isEqualTo(OTHER_PLAN_ID);
        assertThat(member.getPeriodStart()).isNull();
        assertThat(member.getPaidPeriods()).isNull();
        assertThat(MembershipPeriods.hasQueued(member, ZonedDateTime.now(ZONE))).isFalse();
        verify(manualPaymentRepository).save(any(ManualPayment.class));
        verify(eventPublisher).publishEvent(new MembershipPaidEvent(MEMBER_ID));
    }

    @Test
    void unaFechaExplicitaConColaTambienExigeConfirmacion() {
        withQueuedMonths(2);
        LocalDate start = today.minusDays(5);

        assertThatThrownBy(() -> pay(PLAN_ID, "Banco Estado", start, false)).isInstanceOf(PaymentConflictException.class);
        verify(appUserRepository, never()).save(any(AppUser.class));

        pay(PLAN_ID, "Banco Estado", start, true);

        assertThat(member.getPaidAt()).isEqualTo(start.atStartOfDay(ZONE).toInstant());
        assertThat(member.getPaidPeriods()).isNull();
    }

    @Test
    void sinColaElCambioDePlanNoPideConfirmacion() {
        withQueuedMonths(0);

        pay(OTHER_PLAN_ID, "Banco Estado", null, false);

        assertThat(member.getPlanId()).isEqualTo(OTHER_PLAN_ID);
    }

    @Test
    void renovarPorAdelantadoNuncaPideConfirmacionAunqueYaHayaCola() {
        withQueuedMonths(2);

        pay(PLAN_ID, "Banco Estado", null, false);

        assertThat(member.getPaidPeriods()).isEqualTo(4);
    }

    // ---- idempotencia del pago manual

    @Test
    void unSegundoPagoIgualDentroDeLaVentanaResponde409SinAcreditarOtroMes() {
        withQueuedMonths(0);
        when(manualPaymentRepository.existsByMemberIdAndPlanIdAndBankAndCreatedAtAfter(
                        eq(MEMBER_ID), eq(PLAN_ID), eq("Banco Estado"), any(Instant.class)))
                .thenReturn(true);

        assertThatThrownBy(() -> pay(PLAN_ID, "Banco Estado", null, false))
                .isInstanceOfSatisfying(PaymentConflictException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(PaymentConflictException.CODE_DUPLICATE_PAYMENT);
                    assertThat(e.getMessage()).isEqualTo("Ya registraste este pago hace un momento.");
                });

        assertThat(member.getPaidPeriods()).isNull(); // sigue en 1 mes
        verify(appUserRepository, never()).save(any(AppUser.class));
        verify(manualPaymentRepository, never()).save(any(ManualPayment.class));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void laVentanaDeIdempotenciaEsDe15SegundosYAcreditaSiElPagoAnteriorEsMasViejo() {
        withQueuedMonths(0);
        ArgumentCaptor<Instant> after = ArgumentCaptor.forClass(Instant.class);
        when(manualPaymentRepository.existsByMemberIdAndPlanIdAndBankAndCreatedAtAfter(
                        anyLong(), anyLong(), any(String.class), any(Instant.class)))
                .thenReturn(false); // el pago anterior (si hay) es de hace más de 15 s

        pay(PLAN_ID, "Banco Estado", null, false);

        verify(manualPaymentRepository)
                .existsByMemberIdAndPlanIdAndBankAndCreatedAtAfter(eq(MEMBER_ID), eq(PLAN_ID), eq("Banco Estado"), after.capture());
        long secondsBack = Instant.now().getEpochSecond() - after.getValue().getEpochSecond();
        assertThat(secondsBack).isBetween(14L, 16L);
        assertThat(member.getPaidPeriods()).isEqualTo(2); // acreditó
        verify(manualPaymentRepository).save(any(ManualPayment.class));
    }

    // ---- revokePlan con cola

    @Test
    void revocarElPlanCancelaTambienLasReservasDelPeriodoEncoladoYAvisaConElTotal() {
        withQueuedMonths(1);
        LocalDate currentEnd = MembershipPeriods.currentEnd(member, ZonedDateTime.now(ZONE)).toLocalDate();
        Reservation inCurrent = Reservation.builder()
                .memberId(MEMBER_ID).gymBlockId(1L).classDate(today.plusDays(1))
                .status(ReservationStatus.BOOKED).fixedClassId(5L).build();
        Reservation inQueued = Reservation.builder()
                .memberId(MEMBER_ID).gymBlockId(1L).classDate(currentEnd.plusDays(3))
                .status(ReservationStatus.BOOKED).fixedClassId(6L).build();
        when(reservationRepository.findByMemberIdAndStatusAndClassDateGreaterThanEqual(
                        eq(MEMBER_ID), eq(ReservationStatus.BOOKED), any(LocalDate.class)))
                .thenReturn(List.of(inCurrent, inQueued));

        gymService.revokePlan(GYM_ID, MEMBER_ID);

        assertThat(member.getPlanId()).isNull();
        assertThat(member.getPaidPeriods()).isNull();
        assertThat(inCurrent.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(inQueued.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(inQueued.getFixedClassId()).isNull(); // si vuelve a pagar, sus fijas se reservan de nuevo
        verify(reservationRepository).saveAll(List.of(inCurrent, inQueued));
        verify(memberChangeNotifier).planRemoved(gym, MEMBER_ID, 2);
    }
}
