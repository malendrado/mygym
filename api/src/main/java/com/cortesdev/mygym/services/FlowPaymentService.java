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
import com.cortesdev.mygym.services.exception.GymNotFoundException;
import com.cortesdev.mygym.services.exception.GymPlanNotFoundException;
import com.cortesdev.mygym.services.exception.MemberNotFoundException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
@Transactional
public class FlowPaymentService {

    private static final Logger log = LoggerFactory.getLogger(FlowPaymentService.class);

    private final FlowApiClient flowApiClient;
    private final AppUserRepository appUserRepository;
    private final GymPlanRepository gymPlanRepository;
    private final GymRepository gymRepository;
    private final PaymentRepository paymentRepository;
    private final MemberLifecycleEmailService memberLifecycleEmailService;
    private final String returnUrl;
    private final String webhookUrl;

    public FlowPaymentService(
            FlowApiClient flowApiClient,
            AppUserRepository appUserRepository,
            GymPlanRepository gymPlanRepository,
            GymRepository gymRepository,
            PaymentRepository paymentRepository,
            MemberLifecycleEmailService memberLifecycleEmailService,
            @Value("${app.flow.return-url}") String returnUrl,
            @Value("${app.flow.webhook-url}") String webhookUrl) {
        this.flowApiClient = flowApiClient;
        this.appUserRepository = appUserRepository;
        this.gymPlanRepository = gymPlanRepository;
        this.gymRepository = gymRepository;
        this.paymentRepository = paymentRepository;
        this.memberLifecycleEmailService = memberLifecycleEmailService;
        this.returnUrl = returnUrl;
        this.webhookUrl = webhookUrl;
    }

    /** Arranca el pago de un mes — devuelve la URL de Flow a la que hay que redirigir el
     *  navegador completo para que el socio pague (con el medio que prefiera: tarjeta, Webpay,
     *  MACH, Khipu, etc. — Flow decide qué ofrecerle, no elegimos un medio nosotros). */
    public String startCheckout(Long gymId, Long memberId, Long planId) {
        Gym gym = gymRepository.findById(gymId).orElseThrow(() -> new GymNotFoundException(gymId));
        GymPlan plan = gymPlanRepository
                .findByIdAndGymId(planId, gymId)
                .orElseThrow(() -> new GymPlanNotFoundException(gymId, planId));
        AppUser member = appUserRepository
                .findByIdAndGymId(memberId, gymId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));

        Payment payment = paymentRepository.save(Payment.builder()
                .memberId(member.getId())
                .planId(plan.getId())
                .amountClp(plan.getPriceClp())
                .status("PENDING")
                .build());
        String commerceOrder = "mygym-" + payment.getId();
        payment.setCommerceOrder(commerceOrder);
        paymentRepository.save(payment);

        Map<String, String> params = new HashMap<>();
        params.put("commerceOrder", commerceOrder);
        params.put("subject", gym.getName() + " — " + plan.getName());
        params.put("currency", "CLP");
        params.put("amount", String.valueOf(plan.getPriceClp()));
        params.put("email", member.getEmail());
        params.put("urlConfirmation", webhookUrl);
        params.put("urlReturn", returnUrl);

        Map<String, Object> response = flowApiClient.post("/payment/create", params);
        String url = stringOf(response.get("url"));
        String token = stringOf(response.get("token"));
        String flowOrder = stringOf(response.get("flowOrder"));

        payment.setFlowToken(token);
        payment.setFlowOrder(flowOrder);
        paymentRepository.save(payment);

        return url + "?token=" + token;
    }

    /** Llamado por el webhook público — nunca confía en el body del POST, siempre re-consulta a
     *  Flow con el token recibido antes de tocar la base de datos. */
    public void handleWebhook(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        Payment payment = paymentRepository.findByFlowToken(token).orElse(null);
        if (payment == null) {
            log.warn("Webhook de Flow con un token desconocido (no coincide con ningún pago local)");
            return;
        }
        if ("PAID".equals(payment.getStatus())) {
            return; // idempotencia — Flow puede reintentar el mismo webhook más de una vez.
        }

        Map<String, Object> status;
        try {
            status = flowApiClient.get("/payment/getStatus", Map.of("token", token));
        } catch (RestClientException e) {
            log.error("No se pudo confirmar con Flow el pago {}: {}", payment.getId(), e.getMessage());
            return;
        }

        // Flow documenta status numérico: 1=pendiente, 2=pagada, 3=rechazada, 4=anulada.
        String statusCode = stringOf(status.get("status"));
        boolean paid = "2".equals(statusCode) || "paid".equalsIgnoreCase(statusCode);

        payment.setStatus(paid ? "PAID" : "REJECTED");
        payment.setPaidAt(paid ? Instant.now() : null);
        paymentRepository.save(payment);

        if (!paid) {
            return;
        }

        AppUser member = appUserRepository.findById(payment.getMemberId()).orElse(null);
        GymPlan plan = payment.getPlanId() != null ? gymPlanRepository.findById(payment.getPlanId()).orElse(null) : null;
        if (member == null || plan == null) {
            log.error("Pago {} confirmado por Flow pero no se encontró el socio/plan local", payment.getId());
            return;
        }

        member.setPlanId(plan.getId());
        member.setPaidAt(Instant.now());
        appUserRepository.save(member);

        Gym gym = gymRepository.findById(member.getGymId()).orElse(null);
        if (gym != null) {
            List<String> adminEmails = appUserRepository.findByGymIdAndRole(gym.getId(), Role.GYM_ADMIN).stream()
                    .map(AppUser::getEmail)
                    .toList();
            memberLifecycleEmailService.sendPaymentConfirmedMember(gym, member, plan);
            memberLifecycleEmailService.sendPaymentConfirmedAdmin(gym, member, plan, adminEmails);
        }
    }

    private static String stringOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
