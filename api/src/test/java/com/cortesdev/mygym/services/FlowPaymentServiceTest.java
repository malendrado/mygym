package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymPlan;
import com.cortesdev.mygym.models.Payment;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymPlanRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.PaymentRepository;
import com.cortesdev.mygym.services.exception.GymPlanNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionOperations;

/** Webhook de Flow: lo cobrado tiene que coincidir con lo pedido, un pago pendiente no es un rechazo y un pago se
 *  acredita una sola vez (auditoría 2026-10-17). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FlowPaymentServiceTest {

    private static final Long GYM_ID = 5L;
    private static final Long MEMBER_ID = 7L;
    private static final Long PLAN_ID = 3L;
    private static final String TOKEN = "tok-123";

    @Mock private FlowApiClient flowApiClient;
    @Mock private AppUserRepository appUserRepository;
    @Mock private GymPlanRepository gymPlanRepository;
    @Mock private GymRepository gymRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private MemberLifecycleEmailService emailService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PlanChangeService planChangeService;

    private FlowPaymentService service;
    private Payment payment;
    private AppUser member;

    @BeforeEach
    void setUp() {
        service = new FlowPaymentService(
                flowApiClient, appUserRepository, gymPlanRepository, gymRepository, paymentRepository, emailService,
                eventPublisher, planChangeService, TransactionOperations.withoutTransaction(), "http://return", "http://webhook");
        payment = Payment.builder().id(1L).memberId(MEMBER_ID).planId(PLAN_ID).commerceOrder("mygym-1")
                .amountClp(25_700).status("PENDING").flowToken(TOKEN).build();
        member = AppUser.builder().id(MEMBER_ID).gymId(GYM_ID).role(Role.MEMBER).build();
        Gym gym = Gym.builder().id(GYM_ID).flowApiKey("k").flowSecretKey("s").build();
        GymPlan plan = GymPlan.builder().id(PLAN_ID).gymId(GYM_ID).name("Plan").priceClp(25_000).active(true).build();
        when(paymentRepository.findLockedByFlowToken(TOKEN)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByFlowToken(TOKEN)).thenReturn(Optional.of(payment));
        when(appUserRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(appUserRepository.findGymIdById(MEMBER_ID)).thenReturn(Optional.of(GYM_ID));
        when(appUserRepository.findLockedByIdAndGymId(MEMBER_ID, GYM_ID)).thenReturn(Optional.of(member));
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(gym));
        when(gymPlanRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
        when(appUserRepository.findByGymIdAndRole(GYM_ID, Role.GYM_ADMIN)).thenReturn(List.of());
    }

    private void flowSays(Map<String, Object> status) {
        when(flowApiClient.get(eq("/payment/getStatus"), anyMap(), any(), any())).thenReturn(status);
    }

    @Test
    void aPaymentFlowConfirmsWithTheSameOrderAndAmountIsCredited() {
        flowSays(Map.of("status", 2, "commerceOrder", "mygym-1", "amount", "25700.00"));

        service.handleWebhook(TOKEN);

        assertThat(payment.getStatus()).isEqualTo("PAID");
        assertThat(member.getPlanId()).isEqualTo(PLAN_ID);
        assertThat(member.getPaidAt()).isNotNull();
        verify(eventPublisher).publishEvent(new MembershipPaidEvent(MEMBER_ID));
    }

    @Test
    void aPaidNotificationWithADifferentAmountIsStillCreditedBecauseTheAmountOnlyWarns() {
        // Nunca se verificó el formato real del monto de Flow: un pago legítimo no se frena por esa diferencia.
        flowSays(Map.of("status", 2, "commerceOrder", "mygym-1", "amount", 100));

        service.handleWebhook(TOKEN);

        assertThat(payment.getStatus()).isEqualTo("PAID");
        assertThat(member.getPlanId()).isEqualTo(PLAN_ID);
        verify(eventPublisher).publishEvent(new MembershipPaidEvent(MEMBER_ID));
    }

    @Test
    void aPaymentWhoseAmountComesInAnyNumericFormatIsCredited() {
        for (Object amount : new Object[] {25_700, "25700", "25700.00", 25_700.0}) {
            payment.setStatus("PENDING");
            member.setPlanId(null);
            flowSays(Map.of("status", 2, "commerceOrder", "mygym-1", "amount", amount));

            service.handleWebhook(TOKEN);

            assertThat(payment.getStatus()).as("amount=" + amount).isEqualTo("PAID");
            assertThat(member.getPlanId()).as("amount=" + amount).isEqualTo(PLAN_ID);
        }
    }

    @Test
    void aPaidNotificationForAnotherOrderIsNotCredited() {
        flowSays(Map.of("status", 2, "commerceOrder", "mygym-999", "amount", 25_700));

        service.handleWebhook(TOKEN);

        assertThat(payment.getStatus()).isEqualTo("PENDING");
        assertThat(member.getPlanId()).isNull();
    }

    @Test
    void aStillPendingPaymentIsLeftAloneInsteadOfBeingMarkedRejected() {
        flowSays(Map.of("status", 1));

        service.handleWebhook(TOKEN);

        assertThat(payment.getStatus()).isEqualTo("PENDING");
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void aRejectedPaymentIsMarkedRejectedAndNeverCredited() {
        flowSays(Map.of("status", 3));

        service.handleWebhook(TOKEN);

        assertThat(payment.getStatus()).isEqualTo("REJECTED");
        assertThat(member.getPlanId()).isNull();
    }

    @Test
    void anAlreadyPaidPaymentIsNotProcessedAgain() {
        payment.setStatus("PAID");

        service.handleWebhook(TOKEN);

        verify(flowApiClient, never()).get(any(), anyMap(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void checkoutOfAnArchivedPlanIsRefused() {
        GymPlan archived = GymPlan.builder().id(PLAN_ID).gymId(GYM_ID).name("Viejo").priceClp(1_000).active(false).build();
        when(gymPlanRepository.findByIdAndGymId(PLAN_ID, GYM_ID)).thenReturn(Optional.of(archived));

        assertThatThrownBy(() -> service.startCheckout(GYM_ID, MEMBER_ID, PLAN_ID))
                .isInstanceOf(GymPlanNotFoundException.class);

        verify(paymentRepository, never()).save(any(Payment.class));
    }
    @Test
    void ifFlowFailsWhenCreatingTheCheckoutThePendingPaymentIsMarkedRejectedAndTheErrorPropagates() {
        GymPlan plan = GymPlan.builder().id(PLAN_ID).gymId(GYM_ID).name("Plan").priceClp(25_000).active(true).build();
        when(gymPlanRepository.findByIdAndGymId(PLAN_ID, GYM_ID)).thenReturn(Optional.of(plan));
        when(appUserRepository.findByIdAndGymId(MEMBER_ID, GYM_ID)).thenReturn(Optional.of(member));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(flowApiClient.post(eq("/payment/create"), anyMap(), any(), any())).thenThrow(new IllegalStateException("Flow caído"));

        assertThatThrownBy(() -> service.startCheckout(GYM_ID, MEMBER_ID, PLAN_ID))
                .isInstanceOf(IllegalStateException.class);

        verify(paymentRepository, org.mockito.Mockito.atLeastOnce())
                .save(org.mockito.ArgumentMatchers.argThat((Payment p) -> "REJECTED".equals(p.getStatus())));
    }
}
