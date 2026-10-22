package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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

/** Webhook de Flow con renovación anticipada: conserva días con el plan vigente y es idempotente (Mockito, sin DB). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FlowEarlyRenewalTest {

    private static final ZoneId ZONE = ZoneId.of("America/Santiago");
    private static final Long GYM_ID = 5L;
    private static final Long MEMBER_ID = 7L;
    private static final Long PLAN_ID = 3L;
    private static final Long OTHER_PLAN_ID = 4L;
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
    private Gym gym;
    private GymPlan plan;

    @BeforeEach
    void setUp() {
        service = new FlowPaymentService(
                flowApiClient, appUserRepository, gymPlanRepository, gymRepository, paymentRepository, emailService,
                eventPublisher, planChangeService, TransactionOperations.withoutTransaction(), "http://return", "http://webhook");
        payment = Payment.builder().id(1L).memberId(MEMBER_ID).planId(PLAN_ID).commerceOrder("mygym-1")
                .amountClp(25_700).status("PENDING").flowToken(TOKEN).build();
        member = AppUser.builder().id(MEMBER_ID).gymId(GYM_ID).role(Role.MEMBER).build();
        gym = Gym.builder().id(GYM_ID).flowApiKey("k").flowSecretKey("s").build();
        plan = GymPlan.builder().id(PLAN_ID).gymId(GYM_ID).name("Plan").priceClp(25_000).monthlyClasses(8).active(true).build();
        when(paymentRepository.findLockedByFlowToken(TOKEN)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByFlowToken(TOKEN)).thenReturn(Optional.of(payment));
        when(appUserRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(appUserRepository.findGymIdById(MEMBER_ID)).thenReturn(Optional.of(GYM_ID));
        when(appUserRepository.findLockedByIdAndGymId(MEMBER_ID, GYM_ID)).thenReturn(Optional.of(member));
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(gym));
        when(gymPlanRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
        when(appUserRepository.findByGymIdAndRole(GYM_ID, Role.GYM_ADMIN)).thenReturn(List.of());
        when(flowApiClient.get(eq("/payment/getStatus"), anyMap(), any(), any()))
                .thenReturn(Map.of("status", 2, "commerceOrder", "mygym-1", "amount", "25700"));
    }

    private void paidDaysAgo(Long planId, int daysAgo) {
        member.setPlanId(planId);
        member.setPaidAt(ZonedDateTime.now(ZONE).minusDays(daysAgo).toInstant());
    }

    @Test
    void conElPlanVigenteElPagoSeEncolaYConservaLosDias() {
        paidDaysAgo(PLAN_ID, 20);
        ZonedDateTime anchor = MembershipPeriods.anchor(member);

        service.handleWebhook(TOKEN);

        assertThat(payment.getStatus()).isEqualTo("PAID");
        assertThat(MembershipPeriods.finalEnd(member)).isEqualTo(anchor.plusMonths(2));
        assertThat(MembershipPeriods.currentEnd(member, ZonedDateTime.now(ZONE))).isEqualTo(anchor.plusMonths(1));
        assertThat(member.getPaidAt()).isAfter(anchor.plusDays(19).toInstant()); // el pago real es de hoy
        verify(emailService).sendPaymentConfirmedMember(
                gym, member, plan, anchor.plusMonths(1), anchor.plusMonths(2), null);
        verify(eventPublisher).publishEvent(new MembershipPaidEvent(MEMBER_ID));
    }

    @Test
    void unWebhookRepetidoNoEncolaDosVeces() {
        paidDaysAgo(PLAN_ID, 20);
        ZonedDateTime anchor = MembershipPeriods.anchor(member);

        service.handleWebhook(TOKEN);
        service.handleWebhook(TOKEN); // Flow reintenta: el pago ya está en PAID

        assertThat(member.getPaidPeriods()).isEqualTo(2);
        assertThat(MembershipPeriods.finalEnd(member)).isEqualTo(anchor.plusMonths(2));
        verify(appUserRepository, times(1)).save(member);
        verify(eventPublisher, times(1)).publishEvent(any(MembershipPaidEvent.class));
        verify(emailService, times(1)).sendPaymentConfirmedMember(any(), any(), any(), any(), any(), any());
    }

    @Test
    void conPlanVencidoElPagoArrancaDesdeAhoraSinCola() {
        paidDaysAgo(PLAN_ID, 90);
        member.setUsedSessionsAtImport(2);

        service.handleWebhook(TOKEN);

        assertThat(member.getPeriodStart()).isNull();
        assertThat(member.getPaidPeriods()).isNull();
        assertThat(member.getUsedSessionsAtImport()).isNull();
        assertThat(MembershipPeriods.finalEnd(member).toInstant())
                .isEqualTo(member.getPaidAt().atZone(ZONE).plusMonths(1).toInstant());
        verify(emailService).sendPaymentConfirmedMember(gym, member, plan);
    }

    @Test
    void nuncaPagadoArrancaDesdeAhoraSinCola() {
        service.handleWebhook(TOKEN);

        assertThat(member.getPlanId()).isEqualTo(PLAN_ID);
        assertThat(member.getPaidAt()).isNotNull();
        assertThat(member.getPeriodStart()).isNull();
        assertThat(member.getPaidPeriods()).isNull();
        verify(emailService).sendPaymentConfirmedMember(gym, member, plan);
    }

    @Test
    void conOtroPlanVigenteElPagoArrancaPeriodoNuevoDesdeAhora() {
        paidDaysAgo(OTHER_PLAN_ID, 20);

        service.handleWebhook(TOKEN);

        assertThat(member.getPlanId()).isEqualTo(PLAN_ID);
        assertThat(member.getPaidPeriods()).isNull();
        assertThat(MembershipPeriods.hasQueued(member, ZonedDateTime.now(ZONE))).isFalse();
        assertThat(Math.abs(member.getPaidAt().getEpochSecond() - Instant.now().getEpochSecond())).isLessThan(5);
    }

    @Test
    void unSegundoPagoAnticipadoDeOtroCobroSeEncadenaSinPerderLoPagado() {
        paidDaysAgo(PLAN_ID, 20);
        ZonedDateTime anchor = MembershipPeriods.anchor(member);
        service.handleWebhook(TOKEN);
        // otro cobro distinto (otro token) mientras ya tiene la cola
        Payment second = Payment.builder().id(2L).memberId(MEMBER_ID).planId(PLAN_ID).commerceOrder("mygym-2")
                .amountClp(25_700).status("PENDING").flowToken("tok-456").build();
        when(paymentRepository.findLockedByFlowToken("tok-456")).thenReturn(Optional.of(second));
        when(paymentRepository.findByFlowToken("tok-456")).thenReturn(Optional.of(second));
        when(flowApiClient.get(eq("/payment/getStatus"), anyMap(), any(), any()))
                .thenReturn(Map.of("status", 2, "commerceOrder", "mygym-2", "amount", "25700"));

        service.handleWebhook("tok-456");

        assertThat(MembershipPeriods.finalEnd(member)).isEqualTo(anchor.plusMonths(3));
        assertThat(MembershipPeriods.currentEnd(member, ZonedDateTime.now(ZONE))).isEqualTo(anchor.plusMonths(1));
    }

    @Test
    void unPagoDeOtroPlanConMesesEncoladosNuncaSeRechazaPeroDejaUnWarnConLosMesesDescartados() {
        // El dinero ya está cobrado: se acredita igual (cadena nueva) y queda registrado qué se descartó.
        paidDaysAgo(OTHER_PLAN_ID, 20);
        MembershipPeriods.applyPayment(member, OTHER_PLAN_ID, Instant.now(), true);
        MembershipPeriods.applyPayment(member, OTHER_PLAN_ID, Instant.now(), true); // 2 meses en cola
        assertThat(MembershipPeriods.queuedMonths(member, ZonedDateTime.now(ZONE))).isEqualTo(2);
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(FlowPaymentService.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.handleWebhook(TOKEN);
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(payment.getStatus()).isEqualTo("PAID");
        assertThat(member.getPlanId()).isEqualTo(PLAN_ID);
        assertThat(member.getPaidPeriods()).isNull();
        assertThat(member.getPeriodStart()).isNull();
        assertThat(appender.list)
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
                    assertThat(event.getFormattedMessage())
                            .contains("socio " + MEMBER_ID)
                            .contains("plan " + PLAN_ID)
                            .contains("descarta 2 mes");
                });
        verify(eventPublisher).publishEvent(new MembershipPaidEvent(MEMBER_ID));
    }

    @Test
    void renovarElMismoPlanConColaNoDescartaNadaNiAvisa() {
        paidDaysAgo(PLAN_ID, 20);
        MembershipPeriods.applyPayment(member, PLAN_ID, Instant.now(), true);

        service.handleWebhook(TOKEN);

        assertThat(member.getPaidPeriods()).isEqualTo(3);
    }
}
