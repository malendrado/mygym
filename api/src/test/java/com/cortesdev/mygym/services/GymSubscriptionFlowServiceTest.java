package com.cortesdev.mygym.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymSubscriptionPayment;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.GymSubscriptionPaymentRepository;
import com.cortesdev.mygym.services.exception.InvalidSubscriptionException;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

/** Pago con Flow de la suscripcion: el aviso de Flow acredita el mes UNA sola vez y solo si Flow confirma el pago de verdad. */
@ExtendWith(MockitoExtension.class)
class GymSubscriptionFlowServiceTest {

    private static final Long GYM_ID = 5L;

    @Mock private FlowApiClient flowApiClient;
    @Mock private GymSubscriptionService subscriptionService;
    @Mock private GymSubscriptionPaymentRepository paymentRepository;
    @Mock private GymRepository gymRepository;
    @Mock private AppUserRepository appUserRepository;

    private GymSubscriptionFlowService service;

    @BeforeEach
    void setUp() {
        service = new GymSubscriptionFlowService(
                flowApiClient, subscriptionService, paymentRepository, gymRepository, appUserRepository,
                "key", "secret", "https://api/return", "https://api/webhook");
    }

    private GymSubscriptionPayment pending() {
        return GymSubscriptionPayment.builder()
                .id(30L).gymId(GYM_ID).method("FLOW").status("PENDING").amountClp(36_915)
                .commerceOrder("mygymsub-30").flowToken("tok").build();
    }

    @Test
    void aPaidNoticeCreditsTheMonthOnce() {
        GymSubscriptionPayment payment = pending();
        when(paymentRepository.findLockedByFlowToken("tok")).thenReturn(Optional.of(payment));
        when(flowApiClient.get(eq("/payment/getStatus"), eq(Map.of("token", "tok")), eq("key"), eq("secret")))
                .thenReturn(Map.of("status", 2, "commerceOrder", "mygymsub-30"));

        service.handleWebhook("tok");

        verify(subscriptionService).creditFlowPayment(payment);
    }

    @Test
    void aRepeatedNoticeForAnAlreadyCreditedPaymentDoesNothing() {
        GymSubscriptionPayment payment = pending();
        payment.setStatus("PAID");
        when(paymentRepository.findLockedByFlowToken("tok")).thenReturn(Optional.of(payment));

        service.handleWebhook("tok");

        verify(flowApiClient, never()).get(any(), any(), any(), any());
        verify(subscriptionService, never()).creditFlowPayment(any());
    }

    @Test
    void anUnknownTokenIsIgnored() {
        when(paymentRepository.findLockedByFlowToken("zzz")).thenReturn(Optional.empty());

        service.handleWebhook("zzz");

        verify(subscriptionService, never()).creditFlowPayment(any());
    }

    @Test
    void aPaymentStillPendingAtFlowIsLeftAloneUntilTheNextNotice() {
        GymSubscriptionPayment payment = pending();
        when(paymentRepository.findLockedByFlowToken("tok")).thenReturn(Optional.of(payment));
        when(flowApiClient.get(any(), any(), any(), any())).thenReturn(Map.of("status", 1));

        service.handleWebhook("tok");

        assertEquals("PENDING", payment.getStatus());
        verify(subscriptionService, never()).creditFlowPayment(any());
    }

    @Test
    void aRejectedPaymentIsMarkedAndNeverCredited() {
        GymSubscriptionPayment payment = pending();
        when(paymentRepository.findLockedByFlowToken("tok")).thenReturn(Optional.of(payment));
        when(flowApiClient.get(any(), any(), any(), any())).thenReturn(Map.of("status", 3));

        service.handleWebhook("tok");

        assertEquals("REJECTED", payment.getStatus());
        verify(subscriptionService, never()).creditFlowPayment(any());
    }

    @Test
    void anOrderThatDoesNotMatchIsNeverCredited() {
        GymSubscriptionPayment payment = pending();
        when(paymentRepository.findLockedByFlowToken("tok")).thenReturn(Optional.of(payment));
        when(flowApiClient.get(any(), any(), any(), any())).thenReturn(Map.of("status", 2, "commerceOrder", "otra-orden"));

        service.handleWebhook("tok");

        verify(subscriptionService, never()).creditFlowPayment(any());
    }

    @Test
    void ifFlowCannotBeReachedNothingIsDecided() {
        GymSubscriptionPayment payment = pending();
        when(paymentRepository.findLockedByFlowToken("tok")).thenReturn(Optional.of(payment));
        when(flowApiClient.get(any(), any(), any(), any())).thenThrow(new RestClientException("caído"));

        service.handleWebhook("tok");

        assertEquals("PENDING", payment.getStatus());
        verify(subscriptionService, never()).creditFlowPayment(any());
    }

    @Test
    void withoutMygymFlowKeysThereIsNoCheckoutAndTheNoticeIsIgnored() {
        GymSubscriptionFlowService unconfigured = new GymSubscriptionFlowService(
                flowApiClient, subscriptionService, paymentRepository, gymRepository, appUserRepository, "", "", "r", "w");

        assertThrows(InvalidSubscriptionException.class, () -> unconfigured.startCheckout(GYM_ID, 7L));
        unconfigured.handleWebhook("tok");
        verify(paymentRepository, never()).findLockedByFlowToken(any());
    }

    @Test
    void checkoutAsksFlowForTheTotalWithItsFeeAndKeepsTheToken() {
        Gym gym = Gym.builder().id(GYM_ID).name("XaloGym").build();
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(gym));
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(AppUser.builder().id(7L).email("dueno@xalogym.cl").build()));
        GymSubscriptionPayment created = pending();
        created.setFlowToken(null);
        when(subscriptionService.startFlowPayment(GYM_ID)).thenReturn(created);
        when(flowApiClient.post(eq("/payment/create"), any(), eq("key"), eq("secret")))
                .thenReturn(Map.of("url", "https://flow/pay", "token", "tok", "flowOrder", 99));

        String url = service.startCheckout(GYM_ID, 7L);

        assertEquals("https://flow/pay?token=tok", url);
        assertEquals("tok", created.getFlowToken());
        verify(flowApiClient).post(eq("/payment/create"), org.mockito.ArgumentMatchers.argThat(p ->
                "36915".equals(p.get("amount")) && "mygymsub-30".equals(p.get("commerceOrder")) && "dueno@xalogym.cl".equals(p.get("email"))
                        && "https://api/webhook".equals(p.get("urlConfirmation"))), eq("key"), eq("secret"));
        verify(paymentRepository).save(created);
    }
}
