package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymSubscriptionPayment;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.GymSubscriptionPaymentRepository;
import com.cortesdev.mygym.services.exception.GymNotFoundException;
import com.cortesdev.mygym.services.exception.InvalidSubscriptionException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.client.RestClientException;

/**
 * Pago con Flow de la suscripcion de un gimnasio a mygym. A diferencia de los pagos de los socios (que van a la cuenta Flow de
 * cada gimnasio), estos van a la cuenta Flow PROPIA de mygym (variables MYGYM_FLOW_*). Mismo criterio de seguridad que el
 * webhook de socios: nunca se confia en el cuerpo del aviso, solo en lo que Flow responde al reconsultar el token.
 */
@Service
public class GymSubscriptionFlowService {

    private static final Logger log = LoggerFactory.getLogger(GymSubscriptionFlowService.class);

    private final FlowApiClient flowApiClient;
    private final GymSubscriptionService subscriptionService;
    private final GymSubscriptionPaymentRepository paymentRepository;
    private final GymRepository gymRepository;
    private final AppUserRepository appUserRepository;
    private final TransactionOperations tx;
    private final String apiKey;
    private final String secretKey;
    private final String returnUrl;
    private final String webhookUrl;

    public GymSubscriptionFlowService(
            FlowApiClient flowApiClient,
            GymSubscriptionService subscriptionService,
            GymSubscriptionPaymentRepository paymentRepository,
            GymRepository gymRepository,
            AppUserRepository appUserRepository,
            TransactionOperations tx,
            @Value("${app.mygym-flow.api-key:}") String apiKey,
            @Value("${app.mygym-flow.secret-key:}") String secretKey,
            @Value("${app.mygym-flow.return-url:}") String returnUrl,
            @Value("${app.mygym-flow.webhook-url:}") String webhookUrl) {
        this.flowApiClient = flowApiClient;
        this.subscriptionService = subscriptionService;
        this.paymentRepository = paymentRepository;
        this.gymRepository = gymRepository;
        this.appUserRepository = appUserRepository;
        this.tx = tx;
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.returnUrl = returnUrl;
        this.webhookUrl = webhookUrl;
    }

    public boolean isConfigured() {
        return !isBlank(apiKey) && !isBlank(secretKey);
    }

    /** Arranca el pago del proximo mes y devuelve la URL de Flow a la que hay que llevar al administrador. */
    public String startCheckout(Long gymId, Long userId) {
        if (!isConfigured()) {
            throw new InvalidSubscriptionException("El pago con Flow no está disponible por ahora: paga por transferencia y avísanos con «Ya transferí».");
        }
        Gym gym = gymRepository.findById(gymId).orElseThrow(() -> new GymNotFoundException(gymId));
        String email = appUserRepository
                .findById(userId)
                .map(u -> u.getEmail())
                .orElseThrow(() -> new InvalidSubscriptionException("No encontramos tu usuario."));
        GymSubscriptionPayment payment = subscriptionService.startFlowPayment(gymId);

        Map<String, String> params = new HashMap<>();
        params.put("commerceOrder", payment.getCommerceOrder());
        params.put("subject", "Plan mygym — " + gym.getName());
        params.put("currency", "CLP");
        params.put("amount", String.valueOf(payment.getAmountClp()));
        params.put("email", email);
        params.put("urlConfirmation", webhookUrl);
        params.put("urlReturn", returnUrl);
        // Sin transacción: un Flow lento (hasta 20 s) no puede retener una de las 5 conexiones del pool.
        Map<String, Object> response;
        try {
            response = flowApiClient.post("/payment/create", params, apiKey, secretKey);
        } catch (RuntimeException e) {
            // Antes la transacción entera se deshacía y el pago pendiente desaparecía; ahora se marca como rechazado.
            payment.setStatus(GymSubscriptionPayment.REJECTED);
            paymentRepository.save(payment);
            throw e;
        }
        String url = stringOf(response.get("url"));
        String token = stringOf(response.get("token"));
        payment.setFlowToken(token);
        payment.setFlowOrder(stringOf(response.get("flowOrder")));
        paymentRepository.save(payment);
        return url + "?token=" + token;
    }

    /** Aviso de Flow: reconsulta el estado real y, si esta pagado, acredita el mes UNA sola vez (idempotente). */
    public void handleWebhook(String token) {
        if (token == null || token.isBlank() || !isConfigured()) {
            return;
        }
        // Fase 1, sin transacción ni bloqueo: la consulta a Flow no retiene conexión ni la fila del pago.
        GymSubscriptionPayment pending = paymentRepository.findByFlowToken(token).orElse(null);
        if (pending == null) {
            log.warn("Webhook de Flow de suscripción con un token desconocido");
            return;
        }
        if (!GymSubscriptionPayment.PENDING.equals(pending.getStatus())) {
            return; // ya acreditado o rechazado: Flow puede repetir el aviso
        }
        Map<String, Object> status;
        try {
            status = flowApiClient.get("/payment/getStatus", Map.of("token", token), apiKey, secretKey);
        } catch (RestClientException e) {
            log.error("No se pudo confirmar con Flow el pago de suscripción {}: {}", pending.getId(), e.getMessage());
            return;
        }
        String statusCode = stringOf(status.get("status"));
        if ("1".equals(statusCode) || "pending".equalsIgnoreCase(statusCode)) {
            return; // el administrador sigue en el checkout
        }
        boolean paid = "2".equals(statusCode) || "paid".equalsIgnoreCase(statusCode);
        String order = stringOf(status.get("commerceOrder"));
        tx.executeWithoutResult(txStatus -> applyFlowResult(token, paid, order));
    }

    /** Fase 2, en transacción corta y con la fila bloqueada: dos avisos simultáneos acreditan una sola vez. */
    private void applyFlowResult(String token, boolean paid, String order) {
        GymSubscriptionPayment payment = paymentRepository.findLockedByFlowToken(token).orElse(null);
        if (payment == null || !GymSubscriptionPayment.PENDING.equals(payment.getStatus())) {
            return;
        }
        if (paid && order != null && payment.getCommerceOrder() != null && !order.equals(payment.getCommerceOrder())) {
            log.error("Flow confirmó una orden distinta a la del pago de suscripción {}: no se acredita", payment.getId());
            return;
        }
        if (!paid) {
            payment.setStatus(GymSubscriptionPayment.REJECTED);
            paymentRepository.save(payment);
            return;
        }
        subscriptionService.creditFlowPayment(payment);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String stringOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
