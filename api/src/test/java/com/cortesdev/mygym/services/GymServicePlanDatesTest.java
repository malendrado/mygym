package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
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
import com.cortesdev.mygym.services.exception.InvalidPaymentException;
import com.cortesdev.mygym.services.exception.MemberNotFoundException;
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

/** Activar un plan con fecha de inicio y clases ya usadas (Mockito, sin DB). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GymServicePlanDatesTest {

    private static final ZoneId ZONE = ZoneId.of("America/Santiago");
    private static final Long GYM_ID = 4L;
    private static final Long MEMBER_ID = 8L;
    private static final Long PLAN_ID = 12L;
    private static final Long ADMIN_ID = 99L;

    @Mock private GymRepository gymRepository;
    @Mock private GymPlanRepository gymPlanRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private ManualPaymentRepository manualPaymentRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private MemberLifecycleEmailService memberLifecycleEmailService;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock private PlanChangeService planChangeService;
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
        // Los métodos que solo comprueban que el gym existe usan existsById (no cargan la fila completa).
        lenient().when(gymRepository.existsById(GYM_ID)).thenReturn(true);
        when(appUserRepository.findByIdAndGymId(MEMBER_ID, GYM_ID)).thenReturn(Optional.of(member));
        when(appUserRepository.findLockedByIdAndGymId(MEMBER_ID, GYM_ID)).thenReturn(Optional.of(member));
        when(appUserRepository.findByGymIdAndRole(GYM_ID, Role.GYM_ADMIN)).thenReturn(List.of());
        plan8 = planOf(8);
    }

    private GymPlan planOf(Integer monthlyClasses) {
        GymPlan plan = GymPlan.builder()
                .id(PLAN_ID).gymId(GYM_ID).name("Plan Básico").priceClp(35_000).monthlyClasses(monthlyClasses).build();
        when(gymPlanRepository.findByIdAndGymId(PLAN_ID, GYM_ID)).thenReturn(Optional.of(plan));
        return plan;
    }

    private void pay(LocalDate start, Integer used) {
        gymService.simulatePlanPayment(GYM_ID, MEMBER_ID, PLAN_ID, "Banco Estado", ADMIN_ID, start, used);
    }

    @Test
    void conFechaPasadaElPlanArrancaEseDiaYDescuentaLasClasesUsadas() {
        LocalDate start = today.minusDays(14);

        pay(start, 4);

        assertThat(member.getPaidAt()).isEqualTo(start.atStartOfDay(ZONE).toInstant());
        assertThat(member.getUsedSessionsAtImport()).isEqualTo(4);
        ArgumentCaptor<ManualPayment> saved = ArgumentCaptor.forClass(ManualPayment.class);
        verify(manualPaymentRepository).save(saved.capture());
        assertThat(saved.getValue().getPeriodStart()).isEqualTo(start);
        assertThat(saved.getValue().getUsedClassesOutside()).isEqualTo(4);
        // la fecha del registro sigue siendo "ahora" (auditoría), no la del inicio del plan
        assertThat(saved.getValue().getPaidAt()).isAfter(member.getPaidAt());
        verify(eventPublisher).publishEvent(new MembershipPaidEvent(MEMBER_ID));
    }

    @Test
    void elEmailDiceElPeriodoYCuantasClasesLeQuedan() {
        LocalDate start = today.minusDays(14);
        when(reservationRepository.countByMemberIdAndStatusAndClassDateBetween(
                        eq(MEMBER_ID), eq(ReservationStatus.BOOKED), any(), any()))
                .thenReturn(1);

        pay(start, 4);

        ZonedDateTime from = start.atStartOfDay(ZONE);
        // 8 del plan - 4 usadas fuera - 1 reserva en la app = 3
        verify(memberLifecycleEmailService).sendPaymentConfirmedMember(gym, member, plan8, from, from.plusMonths(1), 3);
    }

    @Test
    void sinFechaNiClasesFuncionaComoSiempre() {
        pay(null, null);

        assertThat(member.getPaidAt()).isNotNull();
        assertThat(member.getUsedSessionsAtImport()).isNull();
        ArgumentCaptor<ManualPayment> saved = ArgumentCaptor.forClass(ManualPayment.class);
        verify(manualPaymentRepository).save(saved.capture());
        assertThat(saved.getValue().getPeriodStart()).isEqualTo(today);
        assertThat(saved.getValue().getUsedClassesOutside()).isNull();
    }

    @Test
    void fechaDeHoyEsLoMismoQueSinFecha() {
        pay(today, 0);

        assertThat(member.getUsedSessionsAtImport()).isNull();
    }

    @Test
    void unaFechaFuturaEsInvalida() {
        assertThatThrownBy(() -> pay(today.plusDays(1), null))
                .isInstanceOf(InvalidPaymentException.class)
                .hasMessageContaining("futura");
        verify(manualPaymentRepository, never()).save(any());
        assertThat(member.getPaidAt()).isNull();
    }

    @Test
    void unaFechaDeHaceMasDeUnMesEsInvalidaPorqueYaVencio() {
        assertThatThrownBy(() -> pay(today.minusMonths(1).minusDays(1), null))
                .isInstanceOf(InvalidPaymentException.class)
                .hasMessageContaining("ya habría vencido");
        verify(manualPaymentRepository, never()).save(any());
    }

    @Test
    void justoAntesDeCumplirElMesTodaviaSePuede() {
        pay(today.minusMonths(1).plusDays(1), null);

        assertThat(member.getPaidAt()).isNotNull();
    }

    @Test
    void masClasesUsadasQueElCupoEsInvalido() {
        assertThatThrownBy(() -> pay(today.minusDays(3), 9))
                .isInstanceOf(InvalidPaymentException.class)
                .hasMessageContaining("8");
    }

    @Test
    void usarTodoElCupoSePermite() {
        pay(today.minusDays(3), 8);

        assertThat(member.getUsedSessionsAtImport()).isEqualTo(8);
    }

    @Test
    void enUnPlanLibreLasClasesUsadasSeIgnoran() {
        planOf(null);

        pay(today.minusDays(3), 5);

        assertThat(member.getUsedSessionsAtImport()).isNull();
    }

    @Test
    void renovarBorraLoQueVeniaDeUnaImportacionPeroGuardaLoQueSeDeclaraAhora() {
        member.setUsedSessionsAtImport(6);

        pay(null, null);

        assertThat(member.getUsedSessionsAtImport()).isNull();
    }

    // ---- vista previa ----

    @Test
    void laVistaPreviaCuentaLasReservasQueYaTieneEnLaApp() {
        LocalDate start = today.minusDays(14);
        when(reservationRepository.countByMemberIdAndStatusAndClassDateBetween(
                        eq(MEMBER_ID), eq(ReservationStatus.BOOKED), eq(start), eq(start.plusMonths(1).minusDays(1))))
                .thenReturn(1);

        PaymentPreviewResponse preview = gymService.previewPlanPayment(GYM_ID, MEMBER_ID, PLAN_ID, start, 4);

        assertThat(preview.periodStart()).isEqualTo(start);
        assertThat(preview.periodEnd()).isEqualTo(start.plusMonths(1).minusDays(1));
        assertThat(preview.monthlyClasses()).isEqualTo(8);
        assertThat(preview.usedOutside()).isEqualTo(4);
        assertThat(preview.bookedInApp()).isEqualTo(1);
        assertThat(preview.remaining()).isEqualTo(3);
        assertThat(preview.expiringSoon()).isFalse();
        verify(manualPaymentRepository, never()).save(any());
    }

    @Test
    void laVistaPreviaAvisaSiQuedaPorVencer() {
        PaymentPreviewResponse preview =
                gymService.previewPlanPayment(GYM_ID, MEMBER_ID, PLAN_ID, today.minusMonths(1).plusDays(2), null);

        assertThat(preview.expiringSoon()).isTrue();
    }

    @Test
    void laVistaPreviaDeUnPlanLibreNoTieneTope() {
        planOf(null);

        PaymentPreviewResponse preview = gymService.previewPlanPayment(GYM_ID, MEMBER_ID, PLAN_ID, null, null);

        assertThat(preview.remaining()).isNull();
        assertThat(preview.monthlyClasses()).isNull();
    }

    @Test
    void laVistaPreviaValidaIgualQueConfirmar() {
        assertThatThrownBy(() -> gymService.previewPlanPayment(GYM_ID, MEMBER_ID, PLAN_ID, today.plusDays(2), null))
                .isInstanceOf(InvalidPaymentException.class);
    }

    @Test
    void laVistaPreviaDeUnSocioDeOtroGymEsNotFound() {
        when(appUserRepository.findByIdAndGymId(MEMBER_ID, 77L)).thenReturn(Optional.empty());
        when(appUserRepository.findLockedByIdAndGymId(MEMBER_ID, 77L)).thenReturn(Optional.empty());
        when(gymRepository.existsById(77L)).thenReturn(true);
        when(gymPlanRepository.findByIdAndGymId(PLAN_ID, 77L)).thenReturn(Optional.of(GymPlan.builder().id(PLAN_ID).gymId(77L).build()));

        assertThatThrownBy(() -> gymService.previewPlanPayment(77L, MEMBER_ID, PLAN_ID, null, null))
                .isInstanceOf(MemberNotFoundException.class);
        verify(reservationRepository, never()).countByMemberIdAndStatusAndClassDateBetween(any(), any(), any(), any());
    }
}
