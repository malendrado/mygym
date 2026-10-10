package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymPlan;
import com.cortesdev.mygym.models.ManualPayment;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.BookingRulesRequest;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymPlanRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ManualPaymentRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unidades puras (Mockito, sin DB): pago manual con banco (ManualPayment) y reglas de reserva. */
@ExtendWith(MockitoExtension.class)
class GymServiceManualPaymentTest {

    private static final Long GYM_ID = 4L;
    private static final Long MEMBER_ID = 8L;
    private static final Long PLAN_ID = 12L;
    private static final Long ADMIN_ID = 99L;

    @Mock private GymRepository gymRepository;
    @Mock private GymPlanRepository gymPlanRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private ManualPaymentRepository manualPaymentRepository;
    @Mock private MemberLifecycleEmailService memberLifecycleEmailService;
    @InjectMocks private GymService gymService;

    @Test
    void markingPaidStoresTheBankAmountAndWhoRegisteredIt() {
        Gym gym = Gym.builder().id(GYM_ID).build();
        GymPlan plan = GymPlan.builder().id(PLAN_ID).gymId(GYM_ID).name("Plan Libre").priceClp(45_000).build();
        AppUser member = AppUser.builder().id(MEMBER_ID).gymId(GYM_ID).role(Role.MEMBER).name("Camila").build();
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(gym));
        when(gymPlanRepository.findByIdAndGymId(PLAN_ID, GYM_ID)).thenReturn(Optional.of(plan));
        when(appUserRepository.findByIdAndGymId(MEMBER_ID, GYM_ID)).thenReturn(Optional.of(member));
        when(appUserRepository.findByGymIdAndRole(GYM_ID, Role.GYM_ADMIN)).thenReturn(List.of());

        gymService.simulatePlanPayment(GYM_ID, MEMBER_ID, PLAN_ID, "Banco Estado", ADMIN_ID);

        ArgumentCaptor<ManualPayment> saved = ArgumentCaptor.forClass(ManualPayment.class);
        verify(manualPaymentRepository).save(saved.capture());
        ManualPayment payment = saved.getValue();
        assertThat(payment.getGymId()).isEqualTo(GYM_ID);
        assertThat(payment.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(payment.getPlanId()).isEqualTo(PLAN_ID);
        assertThat(payment.getAmountClp()).isEqualTo(45_000);
        assertThat(payment.getBank()).isEqualTo("Banco Estado");
        assertThat(payment.getRegisteredByUserId()).isEqualTo(ADMIN_ID);
        assertThat(payment.getPaidAt()).isNotNull();
        // el socio sigue quedando con el plan y la fecha de pago, como antes
        assertThat(member.getPlanId()).isEqualTo(PLAN_ID);
        assertThat(member.getPaidAt()).isEqualTo(payment.getPaidAt());
    }

    @Test
    void updatingBookingRulesSavesAllFourValues() {
        Gym gym = Gym.builder().id(GYM_ID).build();
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(gym));

        gymService.updateBookingRules(GYM_ID, new BookingRulesRequest(30, 120, 0, false));

        assertThat(gym.getBookingWindowMinutes()).isEqualTo(30);
        assertThat(gym.getCancellationWindowMinutes()).isEqualTo(120);
        assertThat(gym.getWaitlistHeadStartMinutes()).isZero();
        assertThat(gym.isShowAttendeesToMembers()).isFalse();
        verify(gymRepository).save(any(Gym.class));
    }
}
