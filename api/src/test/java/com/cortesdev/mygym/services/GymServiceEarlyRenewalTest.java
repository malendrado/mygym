package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymPlan;
import com.cortesdev.mygym.models.ManualPayment;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.PaymentPreviewResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymPlanRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ManualPaymentRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
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

/** "Renovar" del admin: conserva los días con el plan vigente; con fecha explícita se comporta como siempre (Mockito, sin DB). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GymServiceEarlyRenewalTest {

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
    private GymPlan plan8;

    @BeforeEach
    void setUp() {
        gym = Gym.builder().id(GYM_ID).name("XaloGym").build();
        member = AppUser.builder().id(MEMBER_ID).gymId(GYM_ID).role(Role.MEMBER).name("Camila").build();
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(gym));
        lenient().when(gymRepository.existsById(GYM_ID)).thenReturn(true);
        when(appUserRepository.findByIdAndGymId(MEMBER_ID, GYM_ID)).thenReturn(Optional.of(member));
        when(appUserRepository.findLockedByIdAndGymId(MEMBER_ID, GYM_ID)).thenReturn(Optional.of(member));
        when(appUserRepository.findByGymIdAndRole(GYM_ID, Role.GYM_ADMIN)).thenReturn(List.of());
        plan8 = plan(PLAN_ID, 8);
        plan(OTHER_PLAN_ID, 12);
    }

    private GymPlan plan(Long id, Integer monthlyClasses) {
        GymPlan plan = GymPlan.builder()
                .id(id).gymId(GYM_ID).name("Plan " + id).priceClp(35_000).monthlyClasses(monthlyClasses).build();
        when(gymPlanRepository.findByIdAndGymId(id, GYM_ID)).thenReturn(Optional.of(plan));
        return plan;
    }

    /** Socio con el plan pagado hace `daysAgo` días (vigente). */
    private void paidDaysAgo(int daysAgo) {
        member.setPlanId(PLAN_ID);
        member.setPaidAt(ZonedDateTime.now(ZONE).minusDays(daysAgo).toInstant());
    }

    private void renew(LocalDate start, Integer used) {
        gymService.simulatePlanPayment(GYM_ID, MEMBER_ID, PLAN_ID, "Banco Estado", ADMIN_ID, start, used);
    }

    @Test
    void renovarSinFechaConElPlanVigenteConservaLosDias() {
        paidDaysAgo(20);
        ZonedDateTime oldEnd = MembershipPeriods.finalEnd(member);
        ZonedDateTime anchor = MembershipPeriods.anchor(member);

        renew(null, null);

        assertThat(MembershipPeriods.finalEnd(member)).isEqualTo(anchor.plusMonths(2));
        assertThat(MembershipPeriods.currentEnd(member, ZonedDateTime.now(ZONE))).isEqualTo(oldEnd);
        assertThat(MembershipPeriods.hasQueued(member, ZonedDateTime.now(ZONE))).isTrue();
        // paidAt es el instante real del pago: hoy, no el inicio de ningún período
        assertThat(member.getPaidAt()).isAfter(anchor.plusDays(19).toInstant());
        ArgumentCaptor<ManualPayment> saved = ArgumentCaptor.forClass(ManualPayment.class);
        verify(manualPaymentRepository).save(saved.capture());
        assertThat(saved.getValue().getPeriodStart()).isEqualTo(oldEnd.toLocalDate()); // período que activa el pago
        verify(eventPublisher).publishEvent(new MembershipPaidEvent(MEMBER_ID));
    }

    @Test
    void renovarConLaFechaDeHoyTambienConservaLosDias() {
        paidDaysAgo(20);
        ZonedDateTime anchor = MembershipPeriods.anchor(member);

        renew(today, null);

        assertThat(MembershipPeriods.finalEnd(member)).isEqualTo(anchor.plusMonths(2));
    }

    @Test
    void elCorreoDiceElPeriodoNuevoYLasClasesDelCupoNuevo() {
        paidDaysAgo(20);
        ZonedDateTime oldEnd = MembershipPeriods.finalEnd(member);
        when(reservationRepository.countByMemberIdAndStatusAndClassDateBetween(
                        eq(MEMBER_ID), eq(ReservationStatus.BOOKED), any(), any()))
                .thenReturn(0);

        renew(null, null);

        verify(memberLifecycleEmailService)
                .sendPaymentConfirmedMember(gym, member, plan8, oldEnd, oldEnd.plusMonths(1), 8);
    }

    @Test
    void conUnaFechaElegidaPorElAdminElComportamientoNoCambia() {
        paidDaysAgo(10);
        LocalDate start = today.minusDays(14);

        renew(start, 4);

        assertThat(member.getPaidAt()).isEqualTo(start.atStartOfDay(ZONE).toInstant());
        assertThat(member.getPeriodStart()).isNull();
        assertThat(member.getPaidPeriods()).isNull();
        assertThat(member.getUsedSessionsAtImport()).isEqualTo(4);
        assertThat(MembershipPeriods.finalEnd(member)).isEqualTo(start.atStartOfDay(ZONE).plusMonths(1));
    }

    @Test
    void conUnaFechaPasadaSinClasesTampocoConservaDias() {
        paidDaysAgo(10);
        LocalDate start = today.minusDays(3);

        renew(start, null);

        assertThat(member.getPaidAt()).isEqualTo(start.atStartOfDay(ZONE).toInstant());
        assertThat(MembershipPeriods.finalEnd(member)).isEqualTo(start.atStartOfDay(ZONE).plusMonths(1));
        assertThat(member.getPaidPeriods()).isNull();
    }

    @Test
    void conClasesYaUsadasDeclaradasArrancaPeriodoNuevoComoSiempre() {
        paidDaysAgo(20);

        renew(null, 3);

        assertThat(member.getPeriodStart()).isNull();
        assertThat(member.getUsedSessionsAtImport()).isEqualTo(3);
        assertThat(MembershipPeriods.hasQueued(member, ZonedDateTime.now(ZONE))).isFalse();
    }

    @Test
    void conPlanVencidoElPagoArrancaDesdeAhoraComoSiempre() {
        paidDaysAgo(90);

        renew(null, null);

        assertThat(member.getPeriodStart()).isNull();
        assertThat(member.getPaidPeriods()).isNull();
        assertThat(Math.abs(member.getPaidAt().getEpochSecond() - java.time.Instant.now().getEpochSecond()))
                .isLessThan(5);
        assertThat(MembershipPeriods.hasQueued(member, ZonedDateTime.now(ZONE))).isFalse();
    }

    @Test
    void nuncaPagadoArrancaDesdeAhora() {
        renew(null, null);

        assertThat(member.getPlanId()).isEqualTo(PLAN_ID);
        assertThat(member.getPeriodStart()).isNull();
        assertThat(MembershipPeriods.finalEnd(member).toInstant())
                .isEqualTo(member.getPaidAt().atZone(ZONE).plusMonths(1).toInstant());
    }

    @Test
    void cambiarDePlanVigenteArrancaPeriodoNuevoSinCola() {
        paidDaysAgo(20);
        member.setPlanId(OTHER_PLAN_ID);

        renew(null, null); // paga PLAN_ID mientras tiene OTHER_PLAN_ID vigente

        assertThat(member.getPlanId()).isEqualTo(PLAN_ID);
        assertThat(member.getPeriodStart()).isNull();
        assertThat(MembershipPeriods.hasQueued(member, ZonedDateTime.now(ZONE))).isFalse();
    }

    @Test
    void unSegundoAnticipadoSeEncadenaSinDerivaDeFinDeMes() {
        paidDaysAgo(20);
        ZonedDateTime anchor = MembershipPeriods.anchor(member);

        renew(null, null);
        renew(null, null);

        assertThat(member.getPaidPeriods()).isEqualTo(3);
        assertThat(MembershipPeriods.finalEnd(member)).isEqualTo(anchor.plusMonths(3));
    }

    @Test
    void laVistaPreviaMuestraElPeriodoNuevoCuandoRenuevaPorAdelantado() {
        paidDaysAgo(20);
        ZonedDateTime oldEnd = MembershipPeriods.finalEnd(member);
        when(reservationRepository.countByMemberIdAndStatusAndClassDateBetween(
                        eq(MEMBER_ID), eq(ReservationStatus.BOOKED), any(), any()))
                .thenReturn(0);

        PaymentPreviewResponse preview = gymService.previewPlanPayment(GYM_ID, MEMBER_ID, PLAN_ID, null, null);

        assertThat(preview.earlyRenewal()).isTrue();
        assertThat(preview.periodStart()).isEqualTo(oldEnd.toLocalDate());
        assertThat(preview.periodEnd()).isEqualTo(oldEnd.plusMonths(1).toLocalDate().minusDays(1));
        assertThat(preview.remaining()).isEqualTo(8);
        assertThat(preview.expiringSoon()).isFalse();
        // la vista previa no modifica nada
        assertThat(member.getPaidPeriods()).isNull();
    }

    @Test
    void laVistaPreviaConFechaElegidaNoEsAnticipada() {
        paidDaysAgo(20);
        LocalDate start = today.minusDays(5);

        PaymentPreviewResponse preview = gymService.previewPlanPayment(GYM_ID, MEMBER_ID, PLAN_ID, start, null);

        assertThat(preview.earlyRenewal()).isFalse();
        assertThat(preview.periodStart()).isEqualTo(start);
    }

    @Test
    void revocarElPlanLimpiaLaCadena() {
        paidDaysAgo(20);
        renew(null, null);

        gymService.revokePlan(GYM_ID, MEMBER_ID);

        assertThat(member.getPlanId()).isNull();
        assertThat(member.getPaidAt()).isNull();
        assertThat(member.getPeriodStart()).isNull();
        assertThat(member.getPaidPeriods()).isNull();
    }
}
