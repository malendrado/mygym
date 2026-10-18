package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymPlan;
import com.cortesdev.mygym.models.ManualPayment;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.PaymentPreviewResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymPlanRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ManualPaymentRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
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

/** Pago manual de otro plan sobre un plan vigente: queda programado, no pisa lo que el socio ya tiene (Mockito, sin DB). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GymServicePlanChangeTest {

    private static final ZoneId ZONE = ZoneId.of("America/Santiago");
    private static final Long GYM_ID = 4L;
    private static final Long MEMBER_ID = 8L;
    private static final Long PLAN_ID = 12L;
    private static final Long NEXT_PLAN_ID = 13L;

    @Mock private GymRepository gymRepository;
    @Mock private GymPlanRepository gymPlanRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private ManualPaymentRepository manualPaymentRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private MemberLifecycleEmailService memberLifecycleEmailService;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock private MemberChangeNotifier memberChangeNotifier;
    @Mock private PlanChangeService planChangeService;
    @InjectMocks private GymService gymService;

    private final LocalDate today = LocalDate.now(ZONE);
    private Gym gym;
    private AppUser member;

    @BeforeEach
    void setUp() {
        gym = Gym.builder().id(GYM_ID).name("XaloGym").build();
        member = AppUser.builder().id(MEMBER_ID).gymId(GYM_ID).role(Role.MEMBER).name("Camila").build();
        member.setPlanId(PLAN_ID);
        member.setPaidAt(ZonedDateTime.now(ZONE).minusDays(10).toInstant());
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(gym));
        when(gymRepository.existsById(GYM_ID)).thenReturn(true);
        when(appUserRepository.findByIdAndGymId(MEMBER_ID, GYM_ID)).thenReturn(Optional.of(member));
        when(appUserRepository.findLockedByIdAndGymId(MEMBER_ID, GYM_ID)).thenReturn(Optional.of(member));
        when(appUserRepository.findByGymIdAndRole(GYM_ID, Role.GYM_ADMIN)).thenReturn(List.of());
        for (Long id : List.of(PLAN_ID, NEXT_PLAN_ID)) {
            when(gymPlanRepository.findByIdAndGymId(id, GYM_ID))
                    .thenReturn(Optional.of(GymPlan.builder()
                            .id(id).gymId(GYM_ID).name("Plan " + id).priceClp(35_000).monthlyClasses(8).build()));
        }
        when(planChangeService.shouldSchedule(any(AppUser.class), anyLong(), any(Instant.class))).thenReturn(true);
    }

    @Test
    void pagarOtroPlanLoDejaProgramadoSinTocarElPlanActual() {
        Instant paidAt = member.getPaidAt();

        gymService.simulatePlanPayment(GYM_ID, MEMBER_ID, NEXT_PLAN_ID, "BancoEstado", 99L, null, null, false);

        assertThat(member.getPlanId()).isEqualTo(PLAN_ID);
        assertThat(member.getPaidAt()).isEqualTo(paidAt);
        assertThat(member.getPendingPlanId()).isEqualTo(NEXT_PLAN_ID);
        assertThat(member.getPendingPlanPeriods()).isEqualTo(1);
        ArgumentCaptor<ManualPayment> payment = ArgumentCaptor.forClass(ManualPayment.class);
        verify(manualPaymentRepository).save(payment.capture());
        assertThat(payment.getValue().getPlanId()).isEqualTo(NEXT_PLAN_ID);
        verify(memberLifecycleEmailService).sendPlanChangeScheduledMember(any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void conFechaElegidaPorElAdminNoSeProgramaYReiniciaLaCadena() {
        gymService.simulatePlanPayment(GYM_ID, MEMBER_ID, NEXT_PLAN_ID, "BancoEstado", 99L, today.minusDays(2), null, false);

        assertThat(member.getPlanId()).isEqualTo(NEXT_PLAN_ID);
        assertThat(member.getPendingPlanId()).isNull();
        verify(memberLifecycleEmailService, never()).sendPlanChangeScheduledMember(any(), any(), any(), any());
    }

    @Test
    void laVistaPreviaAvisaQueElCambioQuedaProgramadoYNoSePierdeNada() {
        PaymentPreviewResponse preview = gymService.previewPlanPayment(GYM_ID, MEMBER_ID, NEXT_PLAN_ID, null, null);

        assertThat(preview.planChangeScheduled()).isTrue();
        assertThat(preview.planChangeFrom()).isEqualTo(MembershipPeriods.finalEnd(member).toLocalDate());
        assertThat(preview.lostPaidMonths()).isZero();
        assertThat(preview.lostDays()).isZero();
        assertThat(preview.earlyRenewal()).isFalse();
    }
}
