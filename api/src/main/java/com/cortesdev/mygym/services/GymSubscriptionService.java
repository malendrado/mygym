package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymSubscription;
import com.cortesdev.mygym.models.GymSubscriptionPayment;
import com.cortesdev.mygym.models.GymSuspension;
import com.cortesdev.mygym.models.MygymPlan;
import com.cortesdev.mygym.models.dto.GymSubscriptionDtos.ActivateRequest;
import com.cortesdev.mygym.models.dto.GymSubscriptionDtos.BankTransferInfo;
import com.cortesdev.mygym.models.dto.GymSubscriptionDtos.BillingInfo;
import com.cortesdev.mygym.models.dto.GymSubscriptionDtos.BillingInfoRequest;
import com.cortesdev.mygym.models.dto.GymSubscriptionDtos.ChangePlanRequest;
import com.cortesdev.mygym.models.dto.GymSubscriptionDtos.ManualPaymentRequest;
import com.cortesdev.mygym.models.dto.GymSubscriptionDtos.PaymentItem;
import com.cortesdev.mygym.models.dto.GymSubscriptionDtos.ReportTransferRequest;
import com.cortesdev.mygym.models.dto.GymSubscriptionDtos.SubscriptionResponse;
import com.cortesdev.mygym.models.dto.GymSubscriptionDtos.SubscriptionUpdateRequest;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.GymSubscriptionPaymentRepository;
import com.cortesdev.mygym.repositories.GymSubscriptionRepository;
import com.cortesdev.mygym.repositories.GymSuspensionRepository;
import com.cortesdev.mygym.repositories.MygymPlanRepository;
import com.cortesdev.mygym.security.GymPauseGuard;
import com.cortesdev.mygym.services.exception.GymNotFoundException;
import com.cortesdev.mygym.services.exception.InvalidSubscriptionException;
import com.cortesdev.mygym.services.exception.SubscriptionConflictException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suscripcion de cada gimnasio a mygym: activacion, pagos (Flow, transferencia o manual), anulaciones, cambio de plan y el
 * estado que decide si el gimnasio queda en pausa por no pago. Las cuentas de fechas y montos viven en {@link GymBilling}.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class GymSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(GymSubscriptionService.class);
    private static final Set<String> METHODS = Set.of(
            GymSubscriptionPayment.FLOW, GymSubscriptionPayment.TRANSFER, GymSubscriptionPayment.MANUAL);

    private final GymRepository gymRepository;
    private final GymSubscriptionRepository subscriptionRepository;
    private final GymSubscriptionPaymentRepository paymentRepository;
    private final GymSuspensionRepository suspensionRepository;
    private final MygymPlanRepository planRepository;
    private final GymPauseGuard gymPauseGuard;
    private final GymBillingNotifier notifier;

    // Cuenta de mygym (Cortes Dev) a la que transfieren los gimnasios. Variables de entorno, nunca escritas en el codigo.
    @Value("${app.mygym-bank.holder:}")
    private String bankHolder;

    @Value("${app.mygym-bank.rut:}")
    private String bankRut;

    @Value("${app.mygym-bank.bank:}")
    private String bankName;

    @Value("${app.mygym-bank.account-type:}")
    private String bankAccountType;

    @Value("${app.mygym-bank.account-number:}")
    private String bankAccountNumber;

    @Value("${app.mygym-bank.email:contacto@mygym.cl}")
    private String bankEmail;

    /** Plan y monto neto que corresponden al PROXIMO pago (si hay un cambio de plan pendiente que ya rige para ese periodo, el nuevo). */
    public record Target(MygymPlan plan, Integer netClp, boolean fromPending) {}

    // ---- Lectura -------------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public SubscriptionResponse get(Long gymId) {
        assertGymExists(gymId);
        GymSubscription sub = subscriptionRepository.findById(gymId).orElseGet(() -> missing(gymId));
        return view(sub);
    }

    private GymSubscription missing(Long gymId) {
        // Gimnasio anterior a este modulo y sin fila: se muestra como "sin iniciar" sin crear nada al leer.
        return GymSubscription.builder().gymId(gymId).billingStatus(GymSubscription.NOT_STARTED).build();
    }

    private SubscriptionResponse view(GymSubscription sub) {
        LocalDate today = GymBilling.today();
        Target target = target(sub, today);
        MygymPlan plan = sub.getPlanId() == null ? null : planRepository.findById(sub.getPlanId()).orElse(null);
        MygymPlan pending = sub.getPendingPlanId() == null ? null : planRepository.findById(sub.getPendingPlanId()).orElse(null);
        GymBilling.Amounts amounts = target.netClp() == null ? null : GymBilling.amounts(target.netClp());
        LocalDate due = GymBilling.dueDate(sub);
        Integer maxUsers = sub.getMaxUsersOverride() != null ? sub.getMaxUsersOverride() : plan == null ? null : plan.getMaxUsers();
        List<PaymentItem> payments = paymentRepository.findTop50ByGymIdOrderByCreatedAtDescIdDesc(sub.getGymId()).stream()
                .map(GymSubscriptionService::item)
                .toList();
        return new SubscriptionResponse(
                sub.getGymId(),
                sub.getBillingStatus(),
                sub.isExempt(),
                plan == null ? null : plan.getId(),
                plan == null ? null : plan.getName(),
                plan == null ? null : plan.getClientsLabel(),
                amounts == null ? null : amounts.net(),
                amounts == null ? null : amounts.iva(),
                amounts == null ? null : amounts.total(),
                amounts == null ? null : amounts.flowFee(),
                amounts == null ? null : amounts.flowTotal(),
                maxUsers,
                sub.getPriceOverrideClp(),
                sub.getMaxUsersOverride(),
                sub.getAnchorDate(),
                due,
                due == null ? null : GymBilling.suspensionDate(due),
                GymBilling.daysToDue(sub, today),
                GymBilling.canRenew(sub, today),
                paymentRepository.existsByGymIdAndStatus(sub.getGymId(), GymSubscriptionPayment.REPORTED),
                sub.getPendingPlanId(),
                pending == null ? null : pending.getName(),
                sub.getPendingPlanFrom(),
                new BillingInfo(sub.getBillingRut(), sub.getBillingName(), sub.getBillingGiro(), sub.getBillingAddress(), sub.getBillingEmail()),
                bank(),
                payments);
    }

    private BankTransferInfo bank() {
        return new BankTransferInfo(bankHolder, bankRut, bankName, bankAccountType, bankAccountNumber, bankEmail);
    }

    private static PaymentItem item(GymSubscriptionPayment p) {
        return new PaymentItem(
                p.getId(),
                p.getMethod(),
                p.getStatus(),
                p.getAmountClp(),
                p.getNetClp(),
                p.getIvaClp(),
                p.getFlowFeeClp(),
                p.getPeriodStart(),
                p.getPeriodEnd(),
                p.getCreatedAt(),
                p.getConfirmedAt(),
                p.getTransferDate(),
                p.getTransferBank(),
                p.getTransferOperation(),
                p.getNote());
    }

    // ---- Alta del gimnasio ---------------------------------------------------------------------------------------------

    /** Todo gimnasio nuevo parte "sin iniciar" (en pausa) hasta que el super-admin lo active al recibir el primer pago. */
    public void createNotStarted(Long gymId) {
        if (!subscriptionRepository.existsById(gymId)) {
            subscriptionRepository.save(GymSubscription.builder()
                    .gymId(gymId)
                    .paidPeriods(0)
                    .billingStatus(GymSubscription.NOT_STARTED)
                    .build());
            gymPauseGuard.invalidate(gymId);
        }
    }

    // ---- Acciones del super-admin --------------------------------------------------------------------------------------

    /** Activa un gimnasio nuevo al recibir su primer pago: fija plan, fecha de inicio y registra ese primer mes como pagado. */
    public SubscriptionResponse activate(Long gymId, ActivateRequest request, Long userId) {
        Gym gym = gymRepository.findById(gymId).orElseThrow(() -> new GymNotFoundException(gymId));
        GymSubscription sub = lock(gymId);
        if (sub.getAnchorDate() != null) {
            throw new SubscriptionConflictException("Este gimnasio ya está activado. Para registrar un pago usa «Registrar pago».");
        }
        MygymPlan plan = activePlan(request.planId());
        String method = method(request.method());
        Integer price = request.priceOverrideClp() != null ? request.priceOverrideClp() : plan.getPriceClp();
        if (price == null) {
            throw new InvalidSubscriptionException("Este plan es a medida: indica el precio mensual de este gimnasio.");
        }
        LocalDate today = GymBilling.today();
        LocalDate start = request.startDate() == null ? today : request.startDate();
        if (start.isBefore(today.minusYears(1)) || start.isAfter(today.plusYears(1))) {
            throw new InvalidSubscriptionException("La fecha de inicio debe estar dentro de un año desde hoy.");
        }

        sub.setPlanId(plan.getId());
        sub.setPriceOverrideClp(request.priceOverrideClp());
        sub.setMaxUsersOverride(request.maxUsersOverride());
        sub.setAnchorDate(start);
        sub.setPaidPeriods(1);
        sub.setExempt(false);
        applyPlanLimits(gym, sub, plan);

        GymBilling.Amounts amounts = GymBilling.amounts(price);
        GymSubscriptionPayment payment = newPayment(gymId, method, GymSubscriptionPayment.PAID, amounts);
        payment.setPeriodStart(start);
        payment.setPeriodEnd(start.plusMonths(1));
        payment.setTransferOperation(request.operation());
        payment.setNote(request.note());
        payment.setConfirmedBy(userId);
        payment.setConfirmedAt(Instant.now());
        payment.setPrevAnchorDate(null);
        payment.setPrevPaidPeriods(0);
        paymentRepository.save(payment);

        refreshStatus(sub, today, false);
        notifier.paid(gym, sub, payment, GymBilling.dueDate(sub));
        return view(sub);
    }

    /** Cambia datos de la suscripcion: precio y cupo propios, fecha de inicio y exencion. */
    public SubscriptionResponse update(Long gymId, SubscriptionUpdateRequest request) {
        Gym gym = gymRepository.findById(gymId).orElseThrow(() -> new GymNotFoundException(gymId));
        GymSubscription sub = lock(gymId);
        sub.setPriceOverrideClp(request.priceOverrideClp());
        sub.setMaxUsersOverride(request.maxUsersOverride());
        if (request.anchorDate() != null) {
            if (sub.getAnchorDate() == null) {
                throw new InvalidSubscriptionException("Primero activa el gimnasio; después podrás cambiar su fecha de inicio.");
            }
            sub.setAnchorDate(request.anchorDate());
        }
        if (request.exempt() != null) {
            sub.setExempt(request.exempt());
        }
        MygymPlan plan = sub.getPlanId() == null ? null : planRepository.findById(sub.getPlanId()).orElse(null);
        if (plan != null) {
            applyPlanLimits(gym, sub, plan);
        }
        refreshStatus(sub, GymBilling.today(), false);
        return view(sub);
    }

    /** Cambio de plan: rige desde el proximo corte, sin prorrateo. Si el gimnasio aun no parte, se asigna de inmediato. */
    public SubscriptionResponse changePlan(Long gymId, ChangePlanRequest request) {
        Gym gym = gymRepository.findById(gymId).orElseThrow(() -> new GymNotFoundException(gymId));
        GymSubscription sub = lock(gymId);
        MygymPlan plan = activePlan(request.planId());
        if (request.priceOverrideClp() == null && plan.getPriceClp() == null) {
            throw new InvalidSubscriptionException("Este plan es a medida: indica el precio mensual de este gimnasio.");
        }
        LocalDate today = GymBilling.today();
        if (sub.getAnchorDate() == null || sub.getPlanId() == null) {
            sub.setPlanId(plan.getId());
            sub.setPriceOverrideClp(request.priceOverrideClp());
            sub.setMaxUsersOverride(null);
            applyPlanLimits(gym, sub, plan);
        } else if (plan.getId().equals(sub.getPlanId())) {
            sub.setPendingPlanId(null);
            sub.setPendingPriceOverrideClp(null);
            sub.setPendingPlanFrom(null);
            sub.setPriceOverrideClp(request.priceOverrideClp());
        } else {
            sub.setPendingPlanId(plan.getId());
            sub.setPendingPriceOverrideClp(request.priceOverrideClp());
            sub.setPendingPlanFrom(GymBilling.nextCutAfter(sub.getAnchorDate(), today));
        }
        subscriptionRepository.save(sub);
        return view(sub);
    }

    public SubscriptionResponse updateBilling(Long gymId, BillingInfoRequest request) {
        assertGymExists(gymId);
        GymSubscription sub = lock(gymId);
        sub.setBillingRut(blankToNull(request.rut()));
        sub.setBillingName(blankToNull(request.name()));
        sub.setBillingGiro(blankToNull(request.giro()));
        sub.setBillingAddress(blankToNull(request.address()));
        sub.setBillingEmail(blankToNull(request.email()));
        subscriptionRepository.save(sub);
        return view(sub);
    }

    /** Pago registrado a mano por el super-admin (sin aviso previo del gimnasio). Siempre se puede, aunque falten mas de 3 dias. */
    public SubscriptionResponse markPaid(Long gymId, ManualPaymentRequest request, Long userId) {
        Gym gym = gymRepository.findById(gymId).orElseThrow(() -> new GymNotFoundException(gymId));
        GymSubscription sub = lock(gymId);
        requireStarted(sub);
        if (paymentRepository.existsByGymIdAndStatus(gymId, GymSubscriptionPayment.REPORTED)) {
            throw new SubscriptionConflictException("Hay un aviso de transferencia sin resolver: confírmalo o recházalo primero.");
        }
        LocalDate today = GymBilling.today();
        Target target = requireTarget(sub, today);
        String method = method(request.method());
        GymBilling.Amounts amounts = GymBilling.amounts(target.netClp());
        GymSubscriptionPayment payment = newPayment(gymId, method, GymSubscriptionPayment.PAID, amounts);
        payment.setTransferOperation(request.operation());
        payment.setNote(request.note());
        applyConfirmed(gym, sub, payment, today, userId);
        return view(sub);
    }

    /** El super-admin confirma que la transferencia que avisó el gimnasio llegó. */
    public SubscriptionResponse confirmTransfer(Long gymId, Long paymentId, Long userId) {
        Gym gym = gymRepository.findById(gymId).orElseThrow(() -> new GymNotFoundException(gymId));
        GymSubscription sub = lock(gymId);
        GymSubscriptionPayment payment = paymentRepository
                .findLockedByIdAndGymId(paymentId, gymId)
                .orElseThrow(() -> new SubscriptionConflictException("No encontramos ese aviso de transferencia."));
        if (!GymSubscriptionPayment.REPORTED.equals(payment.getStatus())) {
            throw new SubscriptionConflictException("Ese aviso de transferencia ya fue resuelto.");
        }
        payment.setStatus(GymSubscriptionPayment.PAID);
        applyConfirmed(gym, sub, payment, GymBilling.today(), userId);
        return view(sub);
    }

    public SubscriptionResponse rejectTransfer(Long gymId, Long paymentId, String reason, Long userId) {
        Gym gym = gymRepository.findById(gymId).orElseThrow(() -> new GymNotFoundException(gymId));
        GymSubscription sub = lock(gymId);
        GymSubscriptionPayment payment = paymentRepository
                .findLockedByIdAndGymId(paymentId, gymId)
                .orElseThrow(() -> new SubscriptionConflictException("No encontramos ese aviso de transferencia."));
        if (!GymSubscriptionPayment.REPORTED.equals(payment.getStatus())) {
            throw new SubscriptionConflictException("Ese aviso de transferencia ya fue resuelto.");
        }
        payment.setStatus(GymSubscriptionPayment.REJECTED);
        payment.setNote(blankToNull(reason));
        payment.setConfirmedBy(userId);
        payment.setConfirmedAt(Instant.now());
        paymentRepository.save(payment);
        notifier.transferRejected(gym, payment.getNote());
        return view(sub);
    }

    /** Anula el ULTIMO pago confirmado (por ejemplo, uno marcado por error): la suscripcion vuelve exactamente a como estaba antes. */
    public SubscriptionResponse voidPayment(Long gymId, Long paymentId) {
        Gym gym = gymRepository.findById(gymId).orElseThrow(() -> new GymNotFoundException(gymId));
        GymSubscription sub = lock(gymId);
        GymSubscriptionPayment payment = paymentRepository
                .findLockedByIdAndGymId(paymentId, gymId)
                .orElseThrow(() -> new SubscriptionConflictException("No encontramos ese pago."));
        if (!GymSubscriptionPayment.PAID.equals(payment.getStatus())) {
            throw new SubscriptionConflictException("Solo se puede anular un pago confirmado.");
        }
        Long lastPaid = paymentRepository
                .findFirstByGymIdAndStatusOrderByIdDesc(gymId, GymSubscriptionPayment.PAID)
                .map(GymSubscriptionPayment::getId)
                .orElse(null);
        if (!paymentId.equals(lastPaid)) {
            throw new SubscriptionConflictException("Solo se puede anular el último pago confirmado.");
        }
        sub.setAnchorDate(payment.getPrevAnchorDate());
        sub.setPaidPeriods(payment.getPrevPaidPeriods() == null ? 0 : payment.getPrevPaidPeriods());
        payment.setStatus(GymSubscriptionPayment.VOIDED);
        payment.setVoidedAt(Instant.now());
        paymentRepository.save(payment);
        refreshStatus(sub, GymBilling.today(), false);
        log.info("Pago {} del gimnasio {} anulado; la suscripción vuelve a {}", paymentId, gym.getId(), sub.getBillingStatus());
        return view(sub);
    }

    // ---- Acciones del administrador del gimnasio -----------------------------------------------------------------------

    /** "Ya transferi": queda a la espera de que el super-admin lo confirme. Solo desde 3 dias antes del vencimiento. */
    public SubscriptionResponse reportTransfer(Long gymId, Long userId, ReportTransferRequest request) {
        Gym gym = gymRepository.findById(gymId).orElseThrow(() -> new GymNotFoundException(gymId));
        GymSubscription sub = lock(gymId);
        LocalDate today = GymBilling.today();
        requireRenewable(sub, today);
        if (paymentRepository.existsByGymIdAndStatus(gymId, GymSubscriptionPayment.REPORTED)) {
            throw new SubscriptionConflictException("Ya nos avisaste de una transferencia: la estamos revisando.");
        }
        LocalDate transferDate = request.transferDate();
        if (transferDate.isAfter(today.plusDays(1)) || transferDate.isBefore(today.minusDays(90))) {
            throw new InvalidSubscriptionException("La fecha de la transferencia no es válida: debe ser de los últimos 90 días y no futura.");
        }
        Target target = requireTarget(sub, today);
        // Lo que se transfiere es el neto mas IVA: la comision de Flow solo se paga cuando se paga con Flow.
        GymSubscriptionPayment payment = newPayment(gymId, GymSubscriptionPayment.TRANSFER, GymSubscriptionPayment.REPORTED, GymBilling.amounts(target.netClp()));
        payment.setAmountClp(GymBilling.amounts(target.netClp()).total());
        payment.setFlowFeeClp(0);
        payment.setTransferDate(request.transferDate());
        payment.setTransferBank(request.bank().trim());
        payment.setTransferOperation(blankToNull(request.operation()));
        payment.setReportedBy(userId);
        payment.setReportedAt(Instant.now());
        paymentRepository.save(payment);
        notifier.transferReported(gym, payment);
        return view(sub);
    }

    // ---- Pago con Flow (lo usa GymSubscriptionFlowService) -------------------------------------------------------------

    /** Crea el pago pendiente de un checkout de Flow: monto = neto + IVA + comision de Flow, que paga el gimnasio. */
    public GymSubscriptionPayment startFlowPayment(Long gymId) {
        assertGymExists(gymId);
        GymSubscription sub = lock(gymId);
        LocalDate today = GymBilling.today();
        requireRenewable(sub, today);
        Target target = requireTarget(sub, today);
        GymBilling.Amounts amounts = GymBilling.amounts(target.netClp());
        GymSubscriptionPayment payment =
                newPayment(gymId, GymSubscriptionPayment.FLOW, GymSubscriptionPayment.PENDING, amounts);
        payment.setAmountClp(amounts.flowTotal());
        payment = paymentRepository.save(payment);
        payment.setCommerceOrder("mygymsub-" + payment.getId());
        return paymentRepository.save(payment);
    }

    /** Flow confirmo el pago: se acredita una sola vez (el llamador ya bloqueo la fila del pago). */
    public void creditFlowPayment(GymSubscriptionPayment payment) {
        Gym gym = gymRepository.findById(payment.getGymId()).orElseThrow(() -> new GymNotFoundException(payment.getGymId()));
        GymSubscription sub = lock(payment.getGymId());
        payment.setStatus(GymSubscriptionPayment.PAID);
        applyConfirmed(gym, sub, payment, GymBilling.today(), null);
    }

    // ---- Estado y cron -------------------------------------------------------------------------------------------------

    /**
     * Recalcula el estado segun las fechas y aplica sus consecuencias: abrir o cerrar el periodo de suspension e invalidar el
     * control de pausa. Devuelve true si el estado cambio.
     */
    public boolean refreshStatus(GymSubscription sub, LocalDate today, boolean fromJob) {
        String before = sub.getBillingStatus();
        String after = GymBilling.statusFor(sub, today);
        boolean changed = !after.equals(before);
        if (changed) {
            sub.setBillingStatus(after);
            boolean wasBlocked = GymSubscription.SUSPENDED.equals(before);
            if (GymSubscription.SUSPENDED.equals(after)) {
                sub.setSuspendedAt(Instant.now());
                suspensionRepository.save(GymSuspension.builder()
                        .gymId(sub.getGymId())
                        .startedAt(sub.getSuspendedAt())
                        .reason("BILLING")
                        .build());
            } else if (wasBlocked) {
                sub.setSuspendedAt(null);
                suspensionRepository
                        .findFirstByGymIdAndEndedAtIsNullOrderByIdDesc(sub.getGymId())
                        .ifPresent(open -> {
                            open.setEndedAt(Instant.now());
                            suspensionRepository.save(open);
                        });
            }
        }
        subscriptionRepository.save(sub);
        if (changed) {
            gymPauseGuard.invalidate(sub.getGymId());
        }
        return changed;
    }

    /** Aplica los cambios de plan que ya llegaron a su fecha de corte. */
    public void promotePendingPlanIfDue(GymSubscription sub, Gym gym, LocalDate today) {
        if (sub.getPendingPlanId() == null || sub.getPendingPlanFrom() == null || today.isBefore(sub.getPendingPlanFrom())) {
            return;
        }
        MygymPlan next = planRepository.findById(sub.getPendingPlanId()).orElse(null);
        if (next != null) {
            sub.setPlanId(next.getId());
            sub.setPriceOverrideClp(sub.getPendingPriceOverrideClp());
            sub.setMaxUsersOverride(null);
            applyPlanLimits(gym, sub, next);
        }
        sub.setPendingPlanId(null);
        sub.setPendingPriceOverrideClp(null);
        sub.setPendingPlanFrom(null);
    }

    // ---- Internos ------------------------------------------------------------------------------------------------------

    /** Aplica un pago ya confirmado: mueve el periodo, recalcula el estado, reactiva si estaba en pausa y avisa. */
    private void applyConfirmed(Gym gym, GymSubscription sub, GymSubscriptionPayment payment, LocalDate today, Long userId) {
        requireStarted(sub);
        payment.setPrevAnchorDate(sub.getAnchorDate());
        payment.setPrevPaidPeriods(sub.getPaidPeriods());
        GymBilling.Applied applied = GymBilling.applyPayment(sub.getAnchorDate(), sub.getPaidPeriods(), today);
        sub.setAnchorDate(applied.anchor());
        sub.setPaidPeriods(applied.paidPeriods());
        payment.setPeriodStart(applied.anchor().plusMonths(applied.paidPeriods() - 1L));
        payment.setPeriodEnd(applied.anchor().plusMonths(applied.paidPeriods()));
        payment.setConfirmedBy(userId);
        payment.setConfirmedAt(Instant.now());
        paymentRepository.save(payment);
        refreshStatus(sub, today, false);
        notifier.paid(gym, sub, payment, GymBilling.dueDate(sub));
    }

    /** Plan y monto del proximo pago; falla con un mensaje claro si el gimnasio no tiene plan o su precio a medida no esta definido. */
    private Target requireTarget(GymSubscription sub, LocalDate today) {
        Target target = target(sub, today);
        if (target.plan() == null) {
            throw new InvalidSubscriptionException("Este gimnasio aún no tiene un plan asignado.");
        }
        if (target.netClp() == null) {
            throw new InvalidSubscriptionException("Tu plan es a medida y todavía no tiene precio: escríbenos a contacto@mygym.cl.");
        }
        return target;
    }

    public Target target(GymSubscription sub, LocalDate today) {
        MygymPlan plan = sub.getPlanId() == null ? null : planRepository.findById(sub.getPlanId()).orElse(null);
        Integer price = sub.getPriceOverrideClp() != null ? sub.getPriceOverrideClp() : plan == null ? null : plan.getPriceClp();
        if (sub.getPendingPlanId() != null && sub.getPendingPlanFrom() != null && sub.getAnchorDate() != null) {
            GymBilling.Applied applied = GymBilling.applyPayment(sub.getAnchorDate(), sub.getPaidPeriods(), today);
            LocalDate periodStart = applied.anchor().plusMonths(applied.paidPeriods() - 1L);
            if (!periodStart.isBefore(sub.getPendingPlanFrom())) {
                MygymPlan pending = planRepository.findById(sub.getPendingPlanId()).orElse(null);
                if (pending != null) {
                    Integer pendingPrice =
                            sub.getPendingPriceOverrideClp() != null ? sub.getPendingPriceOverrideClp() : pending.getPriceClp();
                    return new Target(pending, pendingPrice, true);
                }
            }
        }
        return new Target(plan, price, false);
    }

    private void requireStarted(GymSubscription sub) {
        if (sub.getAnchorDate() == null) {
            throw new SubscriptionConflictException("Este gimnasio aún no está activado.");
        }
    }

    private void requireRenewable(GymSubscription sub, LocalDate today) {
        requireStarted(sub);
        if (sub.isExempt()) {
            throw new SubscriptionConflictException("Este gimnasio no tiene cobro.");
        }
        if (!GymBilling.canRenew(sub, today)) {
            throw new SubscriptionConflictException(
                    "Podrás renovar desde " + GymBilling.RENEW_WINDOW_DAYS + " días antes del vencimiento.");
        }
    }

    private GymSubscription lock(Long gymId) {
        return subscriptionRepository.findLockedByGymId(gymId).orElseGet(() -> {
            assertGymExists(gymId);
            return subscriptionRepository.save(GymSubscription.builder()
                    .gymId(gymId)
                    .paidPeriods(0)
                    .billingStatus(GymSubscription.NOT_STARTED)
                    .build());
        });
    }

    private void assertGymExists(Long gymId) {
        if (!gymRepository.existsById(gymId)) {
            throw new GymNotFoundException(gymId);
        }
    }

    private MygymPlan activePlan(Long planId) {
        MygymPlan plan = planRepository
                .findById(planId)
                .orElseThrow(() -> new InvalidSubscriptionException("Ese plan de mygym no existe."));
        if (!plan.isActive()) {
            throw new InvalidSubscriptionException("Ese plan de mygym está desactivado.");
        }
        return plan;
    }

    private static String method(String raw) {
        String method = raw == null ? "" : raw.trim().toUpperCase();
        if (!METHODS.contains(method)) {
            throw new InvalidSubscriptionException("El medio de pago debe ser FLOW, TRANSFER o MANUAL.");
        }
        return method;
    }

    private static GymSubscriptionPayment newPayment(Long gymId, String method, String status, GymBilling.Amounts amounts) {
        boolean flow = GymSubscriptionPayment.FLOW.equals(method);
        return GymSubscriptionPayment.builder()
                .gymId(gymId)
                .method(method)
                .status(status)
                .netClp(amounts.net())
                .ivaClp(amounts.iva())
                .flowFeeClp(flow ? amounts.flowFee() : 0)
                .amountClp(flow ? amounts.flowTotal() : amounts.total())
                .build();
    }

    /** El cupo de socios del gimnasio sale de su plan, salvo que el super-admin fije uno propio. Escala sin cupo no lo toca. */
    private void applyPlanLimits(Gym gym, GymSubscription sub, MygymPlan plan) {
        Integer maxUsers = sub.getMaxUsersOverride() != null ? sub.getMaxUsersOverride() : plan.getMaxUsers();
        if (maxUsers != null) {
            gym.setMaxUsers(maxUsers);
            gymRepository.save(gym);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
