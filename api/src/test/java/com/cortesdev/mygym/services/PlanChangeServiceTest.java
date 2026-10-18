package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymPlan;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymPlanRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

/** Cambio de plan programado: cuándo se programa y cuándo parte el plan nuevo (clases usadas o fecha), sin DB. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanChangeServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Santiago");
    private static final Long GYM_ID = 4L;
    private static final Long MEMBER_ID = 8L;
    private static final Long PLAN_ID = 12L;
    private static final Long NEXT_PLAN_ID = 13L;

    @Mock private AppUserRepository appUserRepository;
    @Mock private GymPlanRepository gymPlanRepository;
    @Mock private GymRepository gymRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private MemberLifecycleEmailService emailService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private PlanChangeService service;

    private AppUser member;
    private GymPlan current;
    private GymPlan next;

    @BeforeEach
    void setUp() {
        member = AppUser.builder().id(MEMBER_ID).gymId(GYM_ID).role(Role.MEMBER).name("Camila").build();
        current = GymPlan.builder().id(PLAN_ID).gymId(GYM_ID).name("Plan 8").priceClp(30_000).monthlyClasses(8).build();
        next = GymPlan.builder().id(NEXT_PLAN_ID).gymId(GYM_ID).name("Plan 12").priceClp(40_000).monthlyClasses(12).build();
        when(gymPlanRepository.findById(PLAN_ID)).thenReturn(Optional.of(current));
        when(gymPlanRepository.findById(NEXT_PLAN_ID)).thenReturn(Optional.of(next));
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(Gym.builder().id(GYM_ID).name("XaloGym").build()));
        when(appUserRepository.findGymIdById(MEMBER_ID)).thenReturn(Optional.of(GYM_ID));
        when(appUserRepository.findLockedByIdAndGymId(MEMBER_ID, GYM_ID)).thenReturn(Optional.of(member));
        when(reservationRepository.countByMemberIdAndStatusAndClassDateBetween(
                        eq(MEMBER_ID), eq(ReservationStatus.BOOKED), any(), any()))
                .thenReturn(0);
    }

    private void paidDaysAgo(int days) {
        member.setPlanId(PLAN_ID);
        member.setPaidAt(ZonedDateTime.now(ZONE).minusDays(days).toInstant());
    }

    private void usedClasses(int used) {
        when(reservationRepository.countByMemberIdAndStatusAndClassDateBetween(
                        eq(MEMBER_ID), eq(ReservationStatus.BOOKED), any(), any()))
                .thenReturn(used);
    }

    @Test
    void otroPlanConClasesYDiasPorUsarQuedaProgramado() {
        paidDaysAgo(10);
        usedClasses(3);

        assertThat(service.shouldSchedule(member, NEXT_PLAN_ID, Instant.now())).isTrue();
    }

    @Test
    void elMismoPlanNoEsUnCambioDePlan() {
        paidDaysAgo(10);

        assertThat(service.shouldSchedule(member, PLAN_ID, Instant.now())).isFalse();
    }

    @Test
    void conElPlanVencidoElPagoParteDeInmediato() {
        paidDaysAgo(60);

        assertThat(service.shouldSchedule(member, NEXT_PLAN_ID, Instant.now())).isFalse();
    }

    @Test
    void conTodasLasClasesYaUsadasElPagoParteDeInmediato() {
        paidDaysAgo(10);
        usedClasses(8);

        assertThat(service.shouldSchedule(member, NEXT_PLAN_ID, Instant.now())).isFalse();
    }

    @Test
    void conMesesEnColaSiempreQuedaProgramadoAunqueElMesActualEsteAgotado() {
        paidDaysAgo(10);
        MembershipPeriods.applyPayment(member, PLAN_ID, Instant.now(), true);
        usedClasses(8);

        assertThat(service.shouldSchedule(member, NEXT_PLAN_ID, Instant.now())).isTrue();
    }

    @Test
    void unPlanSinTopeDeClasesSoloTerminaPorFecha() {
        current.setMonthlyClasses(null);
        paidDaysAgo(10);
        usedClasses(50);

        assertThat(service.isCurrentPlanFinished(member, Instant.now())).isFalse();
    }

    @Test
    void sinPlanoSinPagoElPlanActualYaNoSirve() {
        assertThat(service.isCurrentPlanFinished(member, Instant.now())).isTrue();
    }

    @Test
    void activarNoHaceNadaMientrasElPlanActualSigaSirviendo() {
        paidDaysAgo(10);
        usedClasses(3);
        MembershipPeriods.schedulePlanChange(member, NEXT_PLAN_ID);

        service.activateIfDue(MEMBER_ID);

        assertThat(member.getPlanId()).isEqualTo(PLAN_ID);
        assertThat(member.getPendingPlanId()).isEqualTo(NEXT_PLAN_ID);
        verify(appUserRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void alAcabarseLasClasesElPlanNuevoPartePorHoy() {
        paidDaysAgo(10);
        usedClasses(8);
        MembershipPeriods.schedulePlanChange(member, NEXT_PLAN_ID);

        service.activateIfDue(MEMBER_ID);

        assertThat(member.getPlanId()).isEqualTo(NEXT_PLAN_ID);
        assertThat(member.getPendingPlanId()).isNull();
        assertThat(member.getPendingPlanPeriods()).isNull();
        assertThat(member.getPaidPeriods()).isNull();
        assertThat(member.getPaidAt()).isBetween(Instant.now().minusSeconds(30), Instant.now().plusSeconds(30));
        verify(appUserRepository).save(member);
        verify(emailService).sendPlanChangeActivated(any(), eq(member), eq(next), any(), any());
        verify(eventPublisher).publishEvent(any(MembershipPaidEvent.class));
    }

    @Test
    void alVencerLaFechaElPlanNuevoParteJustoEnElVencimientoSinHueco() {
        paidDaysAgo(33);
        ZonedDateTime vencio = MembershipPeriods.finalEnd(member);
        MembershipPeriods.schedulePlanChange(member, NEXT_PLAN_ID);

        service.activateIfDue(MEMBER_ID);

        assertThat(member.getPlanId()).isEqualTo(NEXT_PLAN_ID);
        assertThat(member.getPaidAt()).isEqualTo(vencio.toInstant());
        assertThat(member.getPendingPlanId()).isNull();
    }

    @Test
    void siPasoTodoLoPagadoElPlanNuevoParteHoyYNoNaceVencido() {
        paidDaysAgo(90);
        MembershipPeriods.schedulePlanChange(member, NEXT_PLAN_ID);

        service.activateIfDue(MEMBER_ID);

        assertThat(member.getPaidAt()).isBetween(Instant.now().minusSeconds(30), Instant.now().plusSeconds(30));
    }

    @Test
    void variosMesesPagadosDelPlanPendienteArrancanComoCadenaDeEsosMeses() {
        paidDaysAgo(10);
        usedClasses(8);
        MembershipPeriods.schedulePlanChange(member, NEXT_PLAN_ID);
        MembershipPeriods.schedulePlanChange(member, NEXT_PLAN_ID);
        assertThat(member.getPendingPlanPeriods()).isEqualTo(2);

        service.activateIfDue(MEMBER_ID);

        assertThat(member.getPlanId()).isEqualTo(NEXT_PLAN_ID);
        assertThat(member.getPaidPeriods()).isEqualTo(2);
    }

    @Test
    void siElPlanPendienteYaNoExisteSeDescartaElCambio() {
        paidDaysAgo(10);
        usedClasses(8);
        MembershipPeriods.schedulePlanChange(member, NEXT_PLAN_ID);
        when(gymPlanRepository.findById(NEXT_PLAN_ID)).thenReturn(Optional.empty());

        service.activateIfDue(MEMBER_ID);

        assertThat(member.getPlanId()).isEqualTo(PLAN_ID);
        assertThat(member.getPendingPlanId()).isNull();
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void pagarOtroPlanDistintoDelPendienteLoReemplazaYElMismoSumaUnMes() {
        MembershipPeriods.schedulePlanChange(member, NEXT_PLAN_ID);
        MembershipPeriods.schedulePlanChange(member, 99L);

        assertThat(member.getPendingPlanId()).isEqualTo(99L);
        assertThat(member.getPendingPlanPeriods()).isEqualTo(1);
    }

    @Test
    void quitarElPlanDelSocioTambienDescartaElCambioPendiente() {
        paidDaysAgo(5);
        MembershipPeriods.schedulePlanChange(member, NEXT_PLAN_ID);

        MembershipPeriods.clear(member);

        assertThat(member.getPendingPlanId()).isNull();
        assertThat(member.getPendingPlanPeriods()).isNull();
        verify(appUserRepository, never()).save(any());
    }
}
