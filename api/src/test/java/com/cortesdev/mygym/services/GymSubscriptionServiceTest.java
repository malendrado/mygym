package com.cortesdev.mygym.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymSubscription;
import com.cortesdev.mygym.models.GymSubscriptionPayment;
import com.cortesdev.mygym.models.MygymPlan;
import com.cortesdev.mygym.models.dto.GymSubscriptionDtos.ActivateRequest;
import com.cortesdev.mygym.models.dto.GymSubscriptionDtos.BillingInfoRequest;
import com.cortesdev.mygym.models.dto.GymSubscriptionDtos.ChangePlanRequest;
import com.cortesdev.mygym.models.dto.GymSubscriptionDtos.ManualPaymentRequest;
import com.cortesdev.mygym.models.dto.GymSubscriptionDtos.ReportTransferRequest;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.GymSubscriptionPaymentRepository;
import com.cortesdev.mygym.repositories.GymSubscriptionRepository;
import com.cortesdev.mygym.repositories.GymSuspensionRepository;
import com.cortesdev.mygym.repositories.MygymPlanRepository;
import com.cortesdev.mygym.security.GymPauseGuard;
import com.cortesdev.mygym.services.exception.InvalidSubscriptionException;
import com.cortesdev.mygym.services.exception.SubscriptionConflictException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Activacion, pagos, avisos de transferencia, anulacion y cambio de plan de la suscripcion de un gimnasio. */
@ExtendWith(MockitoExtension.class)
class GymSubscriptionServiceTest {

    private static final Long GYM_ID = 5L;

    @Mock private GymRepository gymRepository;
    @Mock private GymSubscriptionRepository subscriptionRepository;
    @Mock private GymSubscriptionPaymentRepository paymentRepository;
    @Mock private GymSuspensionRepository suspensionRepository;
    @Mock private MygymPlanRepository planRepository;
    @Mock private GymPauseGuard gymPauseGuard;
    @Mock private GymBillingNotifier notifier;
    @Mock private com.cortesdev.mygym.repositories.AppUserRepository appUserRepository;
    @Mock private com.cortesdev.mygym.repositories.GymContractAcceptanceRepository contractAcceptanceRepository;
    @InjectMocks private GymSubscriptionService service;

    private final Gym gym = Gym.builder().id(GYM_ID).name("XaloGym").maxUsers(30).active(true).build();
    private final MygymPlan empieza =
            MygymPlan.builder().id(1L).code("EMPIEZA").name("Empieza").priceClp(29_990).maxUsers(100).maxProfesores(10).maxAdmins(5).active(true).build();
    private final MygymPlan crece =
            MygymPlan.builder().id(2L).code("CRECE").name("Crece").priceClp(59_990).maxUsers(200).maxProfesores(10).maxAdmins(5).active(true).build();
    private final MygymPlan escala =
            MygymPlan.builder().id(3L).code("ESCALA").name("Escala").maxProfesores(10).maxAdmins(5).active(true).build();

    @BeforeEach
    void stubs() {
        org.mockito.Mockito.lenient().when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(gym));
        org.mockito.Mockito.lenient().when(gymRepository.existsById(GYM_ID)).thenReturn(true);
        org.mockito.Mockito.lenient().when(planRepository.findById(1L)).thenReturn(Optional.of(empieza));
        org.mockito.Mockito.lenient().when(planRepository.findById(2L)).thenReturn(Optional.of(crece));
        org.mockito.Mockito.lenient().when(planRepository.findById(3L)).thenReturn(Optional.of(escala));
        org.mockito.Mockito.lenient().when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.lenient().when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // Por defecto el gimnasio ya firmo el contrato: los casos de abajo prueban otras reglas de pago.
        org.mockito.Mockito.lenient().when(contractAcceptanceRepository.existsByGymIdAndContractVersion(any(), any())).thenReturn(true);
    }

    private GymSubscription lockedSubscription(GymSubscription sub) {
        when(subscriptionRepository.findLockedByGymId(GYM_ID)).thenReturn(Optional.of(sub));
        return sub;
    }

    private static GymSubscription started(LocalDate anchor, int periods, String status) {
        return GymSubscription.builder()
                .gymId(GYM_ID)
                .planId(1L)
                .anchorDate(anchor)
                .paidPeriods(periods)
                .billingStatus(status)
                .billingRut("76.123.456-7")
                .billingName("Gimnasio SpA")
                .billingGiro("Servicios deportivos")
                .billingAddress("Av. Siempre Viva 123")
                .billingEmail("facturas@gimnasio.cl")
                .build();
    }

    // ---- Datos de factura obligatorios

    @Test
    void payingWithoutCompleteBillingDataIsRefused() {
        GymSubscription sub = lockedSubscription(started(GymBilling.today().minusMonths(1).plusDays(2), 1, "ACTIVE"));
        sub.setBillingGiro(null);

        assertThrows(SubscriptionConflictException.class,
                () -> service.reportTransfer(GYM_ID, 7L, new ReportTransferRequest(GymBilling.today(), "Banco Estado", null)));
        assertThrows(SubscriptionConflictException.class, () -> service.startFlowPayment(GYM_ID));
        verify(paymentRepository, never()).save(any());
    }

    // ---- Contrato firmado obligatorio

    @Test
    void payingWithoutTheSignedContractIsRefused() {
        GymSubscription sub = lockedSubscription(started(GymBilling.today().minusMonths(1).plusDays(2), 1, "ACTIVE"));
        when(contractAcceptanceRepository.existsByGymIdAndContractVersion(GYM_ID, ContractService.VERSION)).thenReturn(false);

        SubscriptionConflictException transfer = assertThrows(SubscriptionConflictException.class,
                () -> service.reportTransfer(GYM_ID, 7L, new ReportTransferRequest(GymBilling.today(), "Banco Estado", null)));
        assertEquals("Antes de pagar firma el contrato de servicios.", transfer.getMessage());
        assertThrows(SubscriptionConflictException.class, () -> service.startFlowPayment(GYM_ID));
        verify(paymentRepository, never()).save(any());
        assertEquals(1L, sub.getPlanId());
    }

    private static BillingInfoRequest billing(String kind, String rut, String name, String giro, String address, String email) {
        return new BillingInfoRequest(kind, rut, name, giro, address, email);
    }

    @Test
    void theGymAdminMustFillEveryBillingField() {
        assertThrows(InvalidSubscriptionException.class,
                () -> service.updateOwnBilling(GYM_ID, billing("COMPANY", "76.831.567-1", "Razón", " ", "Dir", "a@b.cl")));
    }

    @Test
    void theMissingFieldsMessageSaysNameForANaturalPersonAndRazonSocialForACompany() {
        InvalidSubscriptionException person = assertThrows(InvalidSubscriptionException.class,
                () -> service.updateOwnBilling(GYM_ID, billing("PERSON", "76.831.567-1", "", "Giro", "Dir", "a@b.cl")));
        InvalidSubscriptionException company = assertThrows(InvalidSubscriptionException.class,
                () -> service.updateOwnBilling(GYM_ID, billing("COMPANY", "76.831.567-1", "", "Giro", "Dir", "a@b.cl")));
        assertTrue(person.getMessage().contains("nombre"));
        assertTrue(company.getMessage().contains("razón social"));
    }

    @Test
    void anInvalidBillingRutIsRefusedAndNothingIsSaved() {
        assertThrows(InvalidSubscriptionException.class,
                () -> service.updateOwnBilling(GYM_ID, billing("COMPANY", "76.831.567-9", "Gimnasio SpA", "Deportes", "Av. 1", "facturas@gimnasio.cl")));
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void invalidBillingEmailsAreRefused() {
        for (String email : new String[] {"sin-arroba.cl", "juan@gimnasio", "a@b.c", "a b@gimnasio.cl", "juan@@gimnasio.cl", "juan..perez@gimnasio.cl", "juan@gimnasio..cl"}) {
            assertThrows(InvalidSubscriptionException.class,
                    () -> service.updateOwnBilling(GYM_ID, billing("COMPANY", "76.831.567-1", "Gimnasio SpA", "Deportes", "Av. 1", email)),
                    email);
        }
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void validBillingDataIsSavedNormalizedAndKeepsTheKind() {
        GymSubscription sub = lockedSubscription(GymSubscription.builder().gymId(GYM_ID).billingStatus("NOT_STARTED").build());

        service.updateOwnBilling(GYM_ID, billing("PERSON", "768315671", "Ana Pérez", "Servicios deportivos", "Av. 1, Santiago", "  Facturas@Gimnasio.CL "));

        assertEquals("PERSON", sub.getBillingKind());
        assertEquals("76.831.567-1", sub.getBillingRut());
        assertEquals("facturas@gimnasio.cl", sub.getBillingEmail());
        assertEquals("Ana Pérez", sub.getBillingName());
    }

    @Test
    void withoutAKindTheBillingIsACompany() {
        GymSubscription sub = lockedSubscription(GymSubscription.builder().gymId(GYM_ID).billingStatus("NOT_STARTED").build());

        service.updateOwnBilling(GYM_ID, billing(null, "76.831.567-1", "Gimnasio SpA", "Deportes", "Av. 1", "a@gimnasio.cl"));

        assertEquals("COMPANY", sub.getBillingKind());
    }

    @Test
    void anUnknownKindIsRefused() {
        assertThrows(InvalidSubscriptionException.class,
                () -> service.updateBilling(GYM_ID, billing("OTRO", "76.831.567-1", "Gimnasio SpA", "Deportes", "Av. 1", "a@gimnasio.cl")));
    }

    @Test
    void theSuperAdminCanSaveBillingDataLittleByLittleButWhatIsWrittenMustBeValid() {
        GymSubscription sub = lockedSubscription(GymSubscription.builder().gymId(GYM_ID).billingStatus("NOT_STARTED").build());

        service.updateBilling(GYM_ID, billing("COMPANY", "76.831.567-1", null, null, null, null));
        assertEquals("76.831.567-1", sub.getBillingRut());
        assertNull(sub.getBillingEmail());

        assertThrows(InvalidSubscriptionException.class,
                () -> service.updateBilling(GYM_ID, billing("COMPANY", "1-1", null, null, null, null)));
    }

    // ---- El cupo nunca baja de los socios que ya hay

    @Test
    void activatingWithAPlanSmallerThanTheCurrentMembersIsRefusedInsteadOfLeavingTheGymOverQuota() {
        gym.setMaxUsers(300);
        lockedSubscription(GymSubscription.builder().gymId(GYM_ID).billingStatus("NOT_STARTED").build());
        when(appUserRepository.countByGymIdAndRole(GYM_ID, com.cortesdev.mygym.models.Role.MEMBER)).thenReturn(129L);

        assertThrows(InvalidSubscriptionException.class,
                () -> service.activate(GYM_ID, new ActivateRequest(1L, LocalDate.of(2026, 10, 19), null, null, "TRANSFER", null, null), 99L));
        assertEquals(300, gym.getMaxUsers());
    }

    @Test
    void anOwnQuotaAboveTheMembersLetsTheSameGymBeActivated() {
        gym.setMaxUsers(300);
        GymSubscription sub = lockedSubscription(GymSubscription.builder().gymId(GYM_ID).billingStatus("NOT_STARTED").build());
        when(appUserRepository.countByGymIdAndRole(GYM_ID, com.cortesdev.mygym.models.Role.MEMBER)).thenReturn(129L);

        service.activate(GYM_ID, new ActivateRequest(1L, LocalDate.of(2026, 10, 19), null, 150, "TRANSFER", null, null), 99L);

        assertEquals(150, gym.getMaxUsers());
        assertEquals(150, sub.getMaxUsersOverride());
    }

    // ---- Activacion

    @Test
    void activatingAGymRecordsTheFirstMonthAsPaidAndLiftsThePause() {
        GymSubscription sub = lockedSubscription(GymSubscription.builder().gymId(GYM_ID).billingStatus("NOT_STARTED").build());

        service.activate(GYM_ID, new ActivateRequest(1L, LocalDate.of(2026, 10, 19), null, null, "TRANSFER", "OP-1", "primer mes"), 99L);

        assertEquals(LocalDate.of(2026, 10, 19), sub.getAnchorDate());
        assertEquals(1, sub.getPaidPeriods());
        assertEquals(1L, sub.getPlanId());
        assertEquals(100, gym.getMaxUsers()); // el cupo sale del plan
        assertTrue(!"NOT_STARTED".equals(sub.getBillingStatus()));
        verify(gymPauseGuard).invalidate(GYM_ID);
        verify(paymentRepository).save(org.mockito.ArgumentMatchers.argThat(p ->
                "PAID".equals(p.getStatus()) && p.getNetClp() == 29_990 && p.getAmountClp() == 35_688 && p.getFlowFeeClp() == 0
                        && p.getPeriodEnd().equals(LocalDate.of(2026, 11, 19))));
    }

    @Test
    void aCustomOverrideCapsTheGymAndTheFlowMethodAddsTheFeeToWhatWasPaid() {
        GymSubscription sub = lockedSubscription(GymSubscription.builder().gymId(GYM_ID).billingStatus("NOT_STARTED").build());

        service.activate(GYM_ID, new ActivateRequest(3L, null, 120_000, 350, "FLOW", null, null), 99L);

        assertEquals(350, gym.getMaxUsers());
        assertEquals(120_000, sub.getPriceOverrideClp());
        verify(paymentRepository).save(org.mockito.ArgumentMatchers.argThat(p -> p.getFlowFeeClp() > 0 && p.getAmountClp() == p.getNetClp() + p.getIvaClp() + p.getFlowFeeClp()));
    }

    @Test
    void aCustomPlanWithoutAPriceCannotBeActivated() {
        lockedSubscription(GymSubscription.builder().gymId(GYM_ID).billingStatus("NOT_STARTED").build());

        assertThrows(InvalidSubscriptionException.class,
                () -> service.activate(GYM_ID, new ActivateRequest(3L, null, null, null, "MANUAL", null, null), 99L));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void anAlreadyActiveGymCannotBeActivatedTwice() {
        lockedSubscription(started(LocalDate.of(2026, 10, 1), 1, "ACTIVE"));

        assertThrows(SubscriptionConflictException.class,
                () -> service.activate(GYM_ID, new ActivateRequest(1L, null, null, null, "MANUAL", null, null), 99L));
    }

    // ---- Pagos

    @Test
    void aManualPaymentAddsAMonthKeepingTheCutDayAndRemembersTheStateBeforeIt() {
        GymSubscription sub = lockedSubscription(started(LocalDate.now(GymBilling.ZONE).minusDays(10), 1, "ACTIVE"));
        LocalDate anchor = sub.getAnchorDate();

        service.markPaid(GYM_ID, new ManualPaymentRequest("MANUAL", null, "efectivo"), 99L);

        assertEquals(anchor, sub.getAnchorDate());
        assertEquals(2, sub.getPaidPeriods());
        verify(paymentRepository).save(org.mockito.ArgumentMatchers.argThat(p ->
                anchor.equals(p.getPrevAnchorDate()) && Integer.valueOf(1).equals(p.getPrevPaidPeriods())
                        && p.getPeriodStart().equals(anchor.plusMonths(1)) && p.getPeriodEnd().equals(anchor.plusMonths(2))));
    }

    @Test
    void aSuspendedGymThatPaysIsReactivatedRightAway() {
        LocalDate anchor = GymBilling.today().minusMonths(1).minusDays(5); // vencio hace 5 dias: ya suspendido
        GymSubscription sub = lockedSubscription(started(anchor, 1, "SUSPENDED"));
        sub.setSuspendedAt(java.time.Instant.now());

        service.markPaid(GYM_ID, new ManualPaymentRequest("TRANSFER", "OP", null), 99L);

        assertEquals("ACTIVE", sub.getBillingStatus());
        assertNull(sub.getSuspendedAt());
        verify(gymPauseGuard).invalidate(GYM_ID);
    }

    @Test
    void anUnsolvedTransferReportBlocksARegisteredPaymentSoItIsNeverDoubled() {
        lockedSubscription(started(GymBilling.today().minusMonths(1), 1, "ACTIVE"));
        when(paymentRepository.existsByGymIdAndStatus(GYM_ID, "REPORTED")).thenReturn(true);

        assertThrows(SubscriptionConflictException.class,
                () -> service.markPaid(GYM_ID, new ManualPaymentRequest("MANUAL", null, null), 99L));
    }

    // ---- "Ya transferi"

    @Test
    void aTransferCanOnlyBeReportedInTheLastThreeDays() {
        lockedSubscription(started(GymBilling.today().minusDays(5), 1, "ACTIVE")); // vence en ~25 dias

        assertThrows(SubscriptionConflictException.class,
                () -> service.reportTransfer(GYM_ID, 7L, new ReportTransferRequest(GymBilling.today(), "Banco Estado", "123")));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void aReportedTransferIsForTheNetPlusVatWithoutTheFlowFeeAndNotifiesMygym() {
        lockedSubscription(started(GymBilling.today().minusMonths(1).plusDays(2), 1, "ACTIVE")); // vence en 2 dias

        service.reportTransfer(GYM_ID, 7L, new ReportTransferRequest(GymBilling.today(), "Banco Estado", " 123 "));

        verify(paymentRepository).save(org.mockito.ArgumentMatchers.argThat(p ->
                "REPORTED".equals(p.getStatus()) && "TRANSFER".equals(p.getMethod()) && p.getAmountClp() == 35_688
                        && p.getFlowFeeClp() == 0 && "123".equals(p.getTransferOperation())));
        verify(notifier).transferReported(any(), any());
    }

    @Test
    void aTransferDateInTheFutureOrTooOldIsRejected() {
        lockedSubscription(started(GymBilling.today().minusMonths(1).plusDays(2), 1, "ACTIVE"));

        assertThrows(InvalidSubscriptionException.class,
                () -> service.reportTransfer(GYM_ID, 7L, new ReportTransferRequest(GymBilling.today().plusDays(5), "Banco Estado", null)));
        assertThrows(InvalidSubscriptionException.class,
                () -> service.reportTransfer(GYM_ID, 7L, new ReportTransferRequest(GymBilling.today().minusDays(200), "Banco Estado", null)));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void aStartDateMoreThanAYearAwayIsRejected() {
        lockedSubscription(GymSubscription.builder().gymId(GYM_ID).billingStatus("NOT_STARTED").build());

        assertThrows(InvalidSubscriptionException.class,
                () -> service.activate(GYM_ID, new ActivateRequest(1L, GymBilling.today().plusYears(2), null, null, "MANUAL", null, null), 99L));
    }

    @Test
    void onlyOneReportCanBePendingAtATime() {
        lockedSubscription(started(GymBilling.today().minusMonths(1).plusDays(2), 1, "ACTIVE"));
        when(paymentRepository.existsByGymIdAndStatus(GYM_ID, "REPORTED")).thenReturn(true);

        assertThrows(SubscriptionConflictException.class,
                () -> service.reportTransfer(GYM_ID, 7L, new ReportTransferRequest(GymBilling.today(), "Banco Estado", null)));
    }

    @Test
    void assigningAPlanToAnExemptGymLeavesItWaitingForItsFirstPaymentAndThatPaymentActivatesIt() {
        GymSubscription sub = lockedSubscription(GymSubscription.builder().gymId(GYM_ID).exempt(true).billingStatus("EXEMPT").build());

        service.changePlan(GYM_ID, new ChangePlanRequest(2L, null));

        assertEquals("NOT_STARTED", sub.getBillingStatus());
        assertNull(sub.getAnchorDate());
        assertTrue(GymBilling.canRenew(sub, GymBilling.today()));

        GymSubscriptionPayment reported = GymSubscriptionPayment.builder().id(41L).gymId(GYM_ID).method("TRANSFER").status("REPORTED").build();
        when(paymentRepository.findLockedByIdAndGymId(41L, GYM_ID)).thenReturn(Optional.of(reported));

        service.confirmTransfer(GYM_ID, 41L, 99L);

        assertEquals(GymBilling.today(), sub.getAnchorDate());
        assertEquals(1, sub.getPaidPeriods());
        assertEquals("ACTIVE", sub.getBillingStatus());
    }

    @Test
    void confirmingAReportedTransferCreditsTheMonth() {
        GymSubscription sub = lockedSubscription(started(GymBilling.today().minusMonths(1).plusDays(2), 1, "ACTIVE"));
        GymSubscriptionPayment reported = GymSubscriptionPayment.builder().id(40L).gymId(GYM_ID).method("TRANSFER").status("REPORTED").build();
        when(paymentRepository.findLockedByIdAndGymId(40L, GYM_ID)).thenReturn(Optional.of(reported));

        service.confirmTransfer(GYM_ID, 40L, 99L);

        assertEquals("PAID", reported.getStatus());
        assertEquals(2, sub.getPaidPeriods());
        verify(notifier).paid(any(), any(), any(), any());
    }

    @Test
    void rejectingATransferNeverMovesThePeriodAndTellsTheGym() {
        GymSubscription sub = lockedSubscription(started(GymBilling.today().minusMonths(1).plusDays(2), 1, "ACTIVE"));
        GymSubscriptionPayment reported = GymSubscriptionPayment.builder().id(40L).gymId(GYM_ID).method("TRANSFER").status("REPORTED").build();
        when(paymentRepository.findLockedByIdAndGymId(40L, GYM_ID)).thenReturn(Optional.of(reported));

        service.rejectTransfer(GYM_ID, 40L, "no llegó", 99L);

        assertEquals("REJECTED", reported.getStatus());
        assertEquals(1, sub.getPaidPeriods());
        verify(notifier).transferRejected(any(), org.mockito.ArgumentMatchers.eq("no llegó"));
    }

    // ---- Anular

    @Test
    void voidingTheLastPaymentRestoresTheExactPreviousState() {
        LocalDate anchor = GymBilling.today().minusDays(10);
        GymSubscription sub = lockedSubscription(started(anchor, 2, "ACTIVE"));
        GymSubscriptionPayment last = GymSubscriptionPayment.builder()
                .id(50L).gymId(GYM_ID).status("PAID").prevAnchorDate(anchor).prevPaidPeriods(1).build();
        when(paymentRepository.findLockedByIdAndGymId(50L, GYM_ID)).thenReturn(Optional.of(last));
        when(paymentRepository.findFirstByGymIdAndStatusOrderByIdDesc(GYM_ID, "PAID")).thenReturn(Optional.of(last));

        service.voidPayment(GYM_ID, 50L);

        assertEquals(1, sub.getPaidPeriods());
        assertEquals("VOIDED", last.getStatus());
    }

    @Test
    void onlyTheLastConfirmedPaymentCanBeVoided() {
        lockedSubscription(started(GymBilling.today(), 3, "ACTIVE"));
        GymSubscriptionPayment old = GymSubscriptionPayment.builder().id(50L).gymId(GYM_ID).status("PAID").build();
        GymSubscriptionPayment newer = GymSubscriptionPayment.builder().id(51L).gymId(GYM_ID).status("PAID").build();
        when(paymentRepository.findLockedByIdAndGymId(50L, GYM_ID)).thenReturn(Optional.of(old));
        when(paymentRepository.findFirstByGymIdAndStatusOrderByIdDesc(GYM_ID, "PAID")).thenReturn(Optional.of(newer));

        assertThrows(SubscriptionConflictException.class, () -> service.voidPayment(GYM_ID, 50L));
    }

    @Test
    void voidingTheActivationPaymentLeavesTheGymUnstarted() {
        GymSubscription sub = lockedSubscription(started(GymBilling.today(), 1, "ACTIVE"));
        GymSubscriptionPayment first = GymSubscriptionPayment.builder()
                .id(50L).gymId(GYM_ID).status("PAID").prevAnchorDate(null).prevPaidPeriods(0).build();
        when(paymentRepository.findLockedByIdAndGymId(50L, GYM_ID)).thenReturn(Optional.of(first));
        when(paymentRepository.findFirstByGymIdAndStatusOrderByIdDesc(GYM_ID, "PAID")).thenReturn(Optional.of(first));

        service.voidPayment(GYM_ID, 50L);

        assertNull(sub.getAnchorDate());
        assertEquals("NOT_STARTED", sub.getBillingStatus());
    }

    // ---- Cambio de plan

    @Test
    void aPlanChangeRulesFromTheNextCutAndTheNextPaymentUsesTheNewPrice() {
        LocalDate anchor = GymBilling.today().minusDays(10);
        GymSubscription sub = lockedSubscription(started(anchor, 1, "ACTIVE"));

        service.changePlan(GYM_ID, new ChangePlanRequest(2L, null));

        assertEquals(1L, sub.getPlanId()); // sigue con el plan actual hasta el corte
        assertEquals(2L, sub.getPendingPlanId());
        assertEquals(anchor.plusMonths(1), sub.getPendingPlanFrom());
        GymSubscriptionService.Target next = service.target(sub, GymBilling.today());
        assertTrue(next.fromPending());
        assertEquals(59_990, next.netClp());
    }

    @Test
    void thePlanKeepsItsPriceUntilThePeriodBeingPaidStartsAfterTheCut() {
        LocalDate anchor = GymBilling.today().minusMonths(2).plusDays(3); // vence en 3 dias... pero con 2 meses pagados
        GymSubscription sub = started(anchor, 2, "ACTIVE");
        sub.setPendingPlanId(2L);
        sub.setPendingPlanFrom(anchor.plusMonths(3)); // el cambio rige despues del periodo que se paga ahora
        assertFalse(service.target(sub, GymBilling.today()).fromPending());
        assertEquals(29_990, service.target(sub, GymBilling.today()).netClp());
    }

    @Test
    void aGymNotStartedGetsTheNewPlanImmediately() {
        GymSubscription sub = lockedSubscription(GymSubscription.builder().gymId(GYM_ID).billingStatus("NOT_STARTED").build());

        service.changePlan(GYM_ID, new ChangePlanRequest(2L, null));

        assertEquals(2L, sub.getPlanId());
        assertEquals(200, gym.getMaxUsers());
        assertNull(sub.getPendingPlanId());
    }

    @Test
    void promotingAPendingPlanAppliesItsLimitsOnceTheCutArrives() {
        GymSubscription sub = started(GymBilling.today().minusMonths(1), 2, "ACTIVE");
        sub.setPendingPlanId(2L);
        sub.setPendingPriceOverrideClp(null);
        sub.setPendingPlanFrom(GymBilling.today());

        service.promotePendingPlanIfDue(sub, gym, GymBilling.today());

        assertEquals(2L, sub.getPlanId());
        assertNull(sub.getPendingPlanId());
        assertEquals(200, gym.getMaxUsers());
    }

    @Test
    void aPendingPlanThatHasNotArrivedYetIsLeftAlone() {
        GymSubscription sub = started(GymBilling.today(), 1, "ACTIVE");
        sub.setPendingPlanId(2L);
        sub.setPendingPlanFrom(GymBilling.today().plusDays(5));

        service.promotePendingPlanIfDue(sub, gym, GymBilling.today());

        assertEquals(1L, sub.getPlanId());
        assertEquals(2L, sub.getPendingPlanId());
    }

    @Test
    void refreshingTheStatusOpensASuspensionAndBlocksTheGym() {
        GymSubscription sub = started(GymBilling.today().minusMonths(1).minusDays(3), 1, "GRACE");

        boolean changed = service.refreshStatus(sub, GymBilling.today(), true);

        assertTrue(changed);
        assertEquals("SUSPENDED", sub.getBillingStatus());
        verify(suspensionRepository).save(any());
        verify(gymPauseGuard).invalidate(anyLong());
    }
}
