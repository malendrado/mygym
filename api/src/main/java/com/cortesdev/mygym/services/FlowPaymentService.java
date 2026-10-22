package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymPlan;
import com.cortesdev.mygym.models.Payment;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymPlanRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.PaymentRepository;
import com.cortesdev.mygym.services.exception.FlowNotConfiguredException;
import com.cortesdev.mygym.services.exception.GymNotFoundException;
import com.cortesdev.mygym.services.exception.GymPlanNotFoundException;
import com.cortesdev.mygym.services.exception.MemberNotFoundException;
import com.cortesdev.mygym.services.exception.RenewalWindowException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.client.RestClientException;

/**
 * Pago manual mes a mes con Flow.cl — decisión explícita del usuario: mygym no se hace cargo de
 * guardar tarjetas de nadie, así que no se usa la API de suscripciones/cargo automático de Flow.
 * Cada mes es un pago suelto e independiente (mismo mecanismo que cualquier link de pago): el
 * socio paga, listo — nada queda "recordado" de un mes al siguiente del lado de Flow ni del
 * nuestro, más allá del historial de auditoría en la tabla payment.
 *
 * Parámetros y formato de respuesta de /payment/create y /payment/getStatus VERIFICADOS contra
 * el sandbox real de Flow (2026-09-21) — ver FlowApiClient y el SKILL.md del repo. Lo único que
 * queda sin probar de punta a punta es el webhook en sí (necesita una URL pública alcanzable por
 * Flow, no localhost) y el flujo completo de pago con tarjeta de prueba dentro del checkout
 * hosteado de Flow — eso se prueba recién cuando esto esté deployado.
 */
@Service
public class FlowPaymentService {

    private static final Logger log = LoggerFactory.getLogger(FlowPaymentService.class);

    /** Comisión de Flow por link de pago (2,89% + IVA) — se traspasa al socio como recargo
     *  aparte sobre el precio del plan, no la absorbe el gym. Recargo simple (no gross-up):
     *  el gym puede recibir levemente menos que el precio de lista, porque Flow cobra su
     *  comisión sobre el MONTO TOTAL (plan + recargo), no solo sobre el plan. Decisión
     *  explícita del usuario, ver conversación de la feature. Mismo cálculo replicado en el
     *  frontend (member.ts) solo para mostrar el desglose antes de pagar — la fuente de
     *  verdad del monto realmente cobrado es este método. */
    private static final double GATEWAY_COMMISSION_RATE = 0.0289 * 1.19;

    private final FlowApiClient flowApiClient;
    private final AppUserRepository appUserRepository;
    private final GymPlanRepository gymPlanRepository;
    private final GymRepository gymRepository;
    private final PaymentRepository paymentRepository;
    private final MemberLifecycleEmailService memberLifecycleEmailService;
    private final ApplicationEventPublisher eventPublisher;
    private final PlanChangeService planChangeService;
    private final TransactionOperations tx;
    private final String returnUrl;
    private final String webhookUrl;

    public FlowPaymentService(
            FlowApiClient flowApiClient,
            AppUserRepository appUserRepository,
            GymPlanRepository gymPlanRepository,
            GymRepository gymRepository,
            PaymentRepository paymentRepository,
            MemberLifecycleEmailService memberLifecycleEmailService,
            ApplicationEventPublisher eventPublisher,
            PlanChangeService planChangeService,
            TransactionOperations tx,
            @Value("${app.flow.return-url}") String returnUrl,
            @Value("${app.flow.webhook-url}") String webhookUrl) {
        this.flowApiClient = flowApiClient;
        this.appUserRepository = appUserRepository;
        this.gymPlanRepository = gymPlanRepository;
        this.gymRepository = gymRepository;
        this.paymentRepository = paymentRepository;
        this.memberLifecycleEmailService = memberLifecycleEmailService;
        this.eventPublisher = eventPublisher;
        this.planChangeService = planChangeService;
        this.tx = tx;
        this.returnUrl = returnUrl;
        this.webhookUrl = webhookUrl;
    }

    /** Arranca el pago de un mes — devuelve la URL de Flow a la que hay que redirigir el
     *  navegador completo para que el socio pague (con el medio que prefiera: tarjeta, Webpay,
     *  MACH, Khipu, etc. — Flow decide qué ofrecerle, no elegimos un medio nosotros). */
    public String startCheckout(Long gymId, Long memberId, Long planId) {
        // La llamada a Flow va SIN transacción: con el pool de 5 conexiones, un Flow lento (hasta 20 s) no puede retener una.
        Checkout checkout = tx.execute(status -> prepareCheckout(gymId, memberId, planId));
        Map<String, Object> response;
        try {
            response = flowApiClient.post("/payment/create", checkout.params(), checkout.apiKey(), checkout.secretKey());
        } catch (RuntimeException e) {
            // Antes la transacción entera se deshacía y el pago pendiente desaparecía; ahora se marca como rechazado.
            tx.executeWithoutResult(status -> {
                checkout.payment().setStatus("REJECTED");
                paymentRepository.save(checkout.payment());
            });
            throw e;
        }
        String url = stringOf(response.get("url"));
        String token = stringOf(response.get("token"));
        tx.executeWithoutResult(status -> {
            checkout.payment().setFlowToken(token);
            checkout.payment().setFlowOrder(stringOf(response.get("flowOrder")));
            paymentRepository.save(checkout.payment());
        });
        return url + "?token=" + token;
    }

    private record Checkout(Payment payment, Map<String, String> params, String apiKey, String secretKey) {}

    private Checkout prepareCheckout(Long gymId, Long memberId, Long planId) {
        Gym gym = gymRepository.findById(gymId).orElseThrow(() -> new GymNotFoundException(gymId));
        if (isBlank(gym.getFlowApiKey()) || isBlank(gym.getFlowSecretKey())) {
            throw new FlowNotConfiguredException(gymId);
        }
        // Solo planes activos: uno archivado o desactivado (quizá más barato) no se puede pagar llamando a la API por id.
        GymPlan plan = gymPlanRepository
                .findByIdAndGymId(planId, gymId)
                .filter(GymPlan::isActive)
                .orElseThrow(() -> new GymPlanNotFoundException(gymId, planId));
        AppUser member = appUserRepository
                .findByIdAndGymId(memberId, gymId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));

        // Con plan vigente solo se puede renovar en los ultimos dias: antes el boton no existe y la API tampoco lo permite.
        java.time.ZonedDateTime nowZoned = java.time.ZonedDateTime.now(MembershipPeriods.GYM_ZONE);
        if (member.getPaidAt() != null
                && !MembershipPeriods.isExpired(member, nowZoned)
                && !MembershipPeriods.inRenewalWindow(member, nowZoned)) {
            throw new RenewalWindowException("Tu plan está vigente. Podrás renovar desde " + MembershipPeriods.RENEWAL_WINDOW_DAYS + " días antes de que venza.");
        }

        long commission = Math.round(plan.getPriceClp() * GATEWAY_COMMISSION_RATE);
        long totalAmount = plan.getPriceClp() + commission;

        Payment payment = paymentRepository.save(Payment.builder()
                .memberId(member.getId())
                .planId(plan.getId())
                .amountClp((int) totalAmount)
                .status("PENDING")
                .build());
        String commerceOrder = "mygym-" + payment.getId();
        payment.setCommerceOrder(commerceOrder);
        paymentRepository.save(payment);

        Map<String, String> params = new HashMap<>();
        params.put("commerceOrder", commerceOrder);
        params.put("subject", gym.getName() + " — " + plan.getName());
        params.put("currency", "CLP");
        params.put("amount", String.valueOf(totalAmount));
        params.put("email", member.getEmail());
        params.put("urlConfirmation", webhookUrl);
        params.put("urlReturn", returnUrl);

        return new Checkout(payment, params, gym.getFlowApiKey(), gym.getFlowSecretKey());
    }

    /** Llamado por el webhook público — nunca confía en el body del POST, siempre re-consulta a
     *  Flow con el token recibido antes de tocar la base de datos. */
    public void handleWebhook(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        // Fase 1, sin transacción ni bloqueo: la consulta a Flow (hasta 20 s) no puede retener una de las 5 conexiones del pool
        // ni la fila del pago. La idempotencia no depende de esto: se vuelve a revisar con la fila bloqueada en la fase 2.
        Payment pending = paymentRepository.findByFlowToken(token).orElse(null);
        if (pending == null) {
            log.warn("Webhook de Flow con un token desconocido (no coincide con ningún pago local)");
            return;
        }
        if ("PAID".equals(pending.getStatus())) {
            return; // idempotencia — Flow puede reintentar el mismo webhook más de una vez.
        }
        Long memberGymId = appUserRepository.findGymIdById(pending.getMemberId()).orElse(null);
        if (memberGymId == null) {
            log.error("Webhook de Flow para el pago {} pero no se encontró el socio local", pending.getId());
            return;
        }
        Gym gym = gymRepository.findById(memberGymId).orElse(null);
        if (gym == null || isBlank(gym.getFlowApiKey()) || isBlank(gym.getFlowSecretKey())) {
            log.error(
                    "No se pudo confirmar el pago {}: el gimnasio {} no tiene cuenta Flow configurada",
                    pending.getId(),
                    memberGymId);
            return;
        }

        Map<String, Object> status;
        try {
            status = flowApiClient.get(
                    "/payment/getStatus", Map.of("token", token), gym.getFlowApiKey(), gym.getFlowSecretKey());
        } catch (RestClientException e) {
            log.error("No se pudo confirmar con Flow el pago {}: {}", pending.getId(), e.getMessage());
            return;
        }
        // Flow documenta status numérico: 1=pendiente, 2=pagada, 3=rechazada, 4=anulada.
        String statusCode = stringOf(status.get("status"));
        // Aún pendiente (el socio sigue en el checkout): no es un rechazo, se deja como está hasta el próximo aviso.
        if ("1".equals(statusCode) || "pending".equalsIgnoreCase(statusCode)) {
            return;
        }
        boolean paid = "2".equals(statusCode) || "paid".equalsIgnoreCase(statusCode);
        tx.executeWithoutResult(txStatus -> applyFlowResult(token, status, paid, gym, memberGymId));
    }

    /** Fase 2, en transacción corta: con la fila del pago bloqueada hasta el commit, un segundo webhook simultáneo del
     *  mismo pago espera y recién ahí ve PAID (antes ambos acreditaban dos veces, con correos duplicados). Orden de
     *  bloqueos: pago -> socio; ningún otro camino bloquea un pago estando con el socio bloqueado. */
    private void applyFlowResult(String token, Map<String, Object> status, boolean paid, Gym gym, Long memberGymId) {
        Payment payment = paymentRepository.findLockedByFlowToken(token).orElse(null);
        if (payment == null || "PAID".equals(payment.getStatus())) {
            return;
        }
        // La orden la inventamos nosotros ("mygym-<id>") y Flow la devuelve tal cual: si no coincide, no es este pago.
        if (paid && !orderMatches(payment, status)) {
            log.error("Flow confirmó como pagado un cobro de otra orden que el pago local {}: no se acredita", payment.getId());
            return;
        }
        // El monto solo AVISA: nunca se verificó contra el formato real que devuelve Flow, y bloquear un pago legítimo
        // por una diferencia de formato (o de comisión) es peor que dejar pasar el aviso. Si en los registros no aparece
        // nunca, se puede volver estricto con datos en la mano.
        if (paid && !amountMatches(payment, status)) {
            log.warn(
                    "Flow informa un monto distinto al pedido en el pago local {} (pedido: {}, Flow: {}); se acredita igual",
                    payment.getId(), payment.getAmountClp(), stringOf(status.get("amount")));
        }

        payment.setStatus(paid ? "PAID" : "REJECTED");
        payment.setPaidAt(paid ? Instant.now() : null);
        paymentRepository.save(payment);

        if (!paid) {
            return;
        }

        GymPlan plan = payment.getPlanId() != null ? gymPlanRepository.findById(payment.getPlanId()).orElse(null) : null;
        if (plan == null) {
            log.error("Pago {} confirmado por Flow pero no se encontró el plan local", payment.getId());
            return;
        }

        // Renovación anticipada: pagar el MISMO plan vigente conserva los días que le quedan (el período nuevo parte en el
        // vencimiento actual y queda en cola); cualquier otro caso (vencido, nunca pagó, otro plan) arranca período desde
        // ahora y no arrastra lo que traía de una importación (ver AppUser.usedSessionsAtImport). Un webhook repetido no
        // llega acá: la fila del pago ya quedó en PAID arriba (idempotencia por token).
        // Dos pagos distintos del mismo socio en paralelo (Flow + Flow, o Flow + manual) se serializan acá: el segundo lee
        // el paidPeriods ya actualizado por el primero, en vez de escribir el mismo N+1 y perder un mes pagado.
        AppUser member = appUserRepository.findLockedByIdAndGymId(payment.getMemberId(), memberGymId).orElse(null);
        if (member == null) {
            log.error("Webhook de Flow para el pago {} pero el socio local ya no existe", payment.getId());
            return;
        }
        Instant now = Instant.now();
        // Cambio de plan: otro plan sobre un plan vigente queda en espera (no pisa lo que le queda); ver PlanChangeService.
        if (planChangeService.shouldSchedule(member, plan.getId(), now)) {
            java.time.ZonedDateTime currentEnd = MembershipPeriods.finalEnd(member);
            MembershipPeriods.schedulePlanChange(member, plan.getId());
            appUserRepository.save(member);
            List<String> scheduledAdminEmails = appUserRepository.findByGymIdAndRole(gym.getId(), Role.GYM_ADMIN).stream()
                    .map(AppUser::getEmail)
                    .toList();
            memberLifecycleEmailService.sendPlanChangeScheduledMember(gym, member, plan, currentEnd);
            memberLifecycleEmailService.sendPaymentConfirmedAdmin(gym, member, plan, scheduledAdminEmails);
            return;
        }
        java.time.ZonedDateTime queuedStart = MembershipPeriods.canExtend(member, plan.getId(), now)
                ? MembershipPeriods.endOf(MembershipPeriods.anchor(member), MembershipPeriods.paidPeriods(member))
                : null;
        // El dinero ya está cobrado: nunca se rechaza. Si el pago no conserva la cadena (otro plan o plan vencido) y traía
        // meses pagados por adelantado, se descartan (comportamiento histórico) pero queda registrado.
        if (queuedStart == null) {
            int lostMonths = MembershipPeriods.queuedMonths(member, now.atZone(MembershipPeriods.GYM_ZONE));
            if (lostMonths > 0) {
                log.warn("Pago Flow {} del socio {} (plan {}) reinicia la cadena y descarta {} mes(es) pagados por adelantado",
                        payment.getId(), member.getId(), plan.getId(), lostMonths);
            }
        }
        boolean early = MembershipPeriods.applyPayment(member, plan.getId(), now, true);
        appUserRepository.save(member);

        List<String> adminEmails = appUserRepository.findByGymIdAndRole(gym.getId(), Role.GYM_ADMIN).stream()
                .map(AppUser::getEmail)
                .toList();
        if (early) {
            // Con fechas explícitas: el período nuevo [vencimiento anterior, nuevo vencimiento).
            memberLifecycleEmailService.sendPaymentConfirmedMember(
                    gym, member, plan, queuedStart, MembershipPeriods.finalEnd(member), null);
        } else {
            memberLifecycleEmailService.sendPaymentConfirmedMember(gym, member, plan);
        }
        memberLifecycleEmailService.sendPaymentConfirmedAdmin(gym, member, plan, adminEmails);
        // Reserva sus clases fijas del período nuevo, después del commit (ver FixedClassService).
        eventPublisher.publishEvent(new MembershipPaidEvent(member.getId()));
    }

    /** Solo se compara si la respuesta trae la orden (si no viene, no se inventa una diferencia). */
    private static boolean orderMatches(Payment payment, Map<String, Object> flowStatus) {
        String order = stringOf(flowStatus.get("commerceOrder"));
        return order == null || payment.getCommerceOrder() == null || order.equals(payment.getCommerceOrder());
    }

    /** Acepta "25700", 25700 y "25700.00"; si la respuesta no trae monto no se inventa una diferencia. */
    private static boolean amountMatches(Payment payment, Map<String, Object> flowStatus) {
        String amount = stringOf(flowStatus.get("amount"));
        if (amount != null && payment.getAmountClp() != null) {
            try {
                return new java.math.BigDecimal(amount).compareTo(java.math.BigDecimal.valueOf(payment.getAmountClp())) == 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String stringOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
