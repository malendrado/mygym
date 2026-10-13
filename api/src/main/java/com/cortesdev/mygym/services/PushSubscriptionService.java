package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.PushSubscription;
import com.cortesdev.mygym.repositories.PushSubscriptionRepository;
import com.cortesdev.mygym.services.PushMessage.Urgency;
import com.cortesdev.mygym.services.exception.InvalidPushSubscriptionException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Alta y baja de suscripciones push de un usuario, y el aviso de prueba. */
@Service
@RequiredArgsConstructor
@Transactional
public class PushSubscriptionService {

    /** Por usuario: mas telefonos que esto no es un uso normal; se descartan los mas antiguos. */
    static final int MAX_SUBSCRIPTIONS_PER_USER = 10;
    private static final int MAX_TESTS_PER_HOUR = 3;
    private static final Duration TEST_WINDOW = Duration.ofHours(1);
    private static final int P256DH_BYTES = 65;
    private static final int AUTH_BYTES = 16;

    private final PushSubscriptionRepository repository;
    private final PushNotificationService pushService;
    private final PushSender sender;

    // Contador en memoria: la API corre en una sola instancia (ver ClassReminderJob).
    private final Map<Long, List<Instant>> recentTests = new ConcurrentHashMap<>();

    public String publicKey() {
        return sender.publicKey();
    }

    /**
     * Registra (o reasigna) el dispositivo. El endpoint es unico: si ya existia (el mismo telefono, o un telefono
     * compartido donde ahora inicio sesion otra persona) la fila pasa al usuario actual.
     */
    public void register(Long userId, String endpoint, String p256dh, String auth, String userAgent) {
        if (!PushEndpointPolicy.isAllowed(endpoint)) {
            throw new InvalidPushSubscriptionException("La dirección de avisos no es válida");
        }
        requireBase64Url(p256dh, P256DH_BYTES, "clave");
        requireBase64Url(auth, AUTH_BYTES, "secreto");

        PushSubscription subscription = repository.findByEndpoint(endpoint).orElseGet(PushSubscription::new);
        subscription.setUserId(userId);
        subscription.setEndpoint(endpoint);
        subscription.setP256dh(p256dh);
        subscription.setAuth(auth);
        subscription.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 300)));
        subscription.setFailureCount(0);
        repository.save(subscription);

        List<PushSubscription> mine = repository.findByUserId(userId);
        if (mine.size() > MAX_SUBSCRIPTIONS_PER_USER) {
            mine.stream()
                    .sorted(Comparator.comparing(PushSubscription::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .limit(mine.size() - MAX_SUBSCRIPTIONS_PER_USER)
                    .forEach(repository::delete);
        }
    }

    /** Solo borra la suscripcion si pertenece a quien la pide. */
    public void unregister(Long userId, String endpoint) {
        if (endpoint != null) {
            repository.deleteByEndpointAndUserId(endpoint, userId);
        }
    }

    /** Aviso de prueba a los dispositivos del propio usuario. Devuelve false si supero el limite por hora. */
    public boolean sendTest(Long userId) {
        Instant now = Instant.now();
        List<Instant> history = recentTests.computeIfAbsent(userId, id -> new java.util.concurrent.CopyOnWriteArrayList<>());
        history.removeIf(at -> at.isBefore(now.minus(TEST_WINDOW)));
        if (history.size() >= MAX_TESTS_PER_HOUR) {
            return false;
        }
        history.add(now);
        pushService.notifyUsers(
                List.of(userId),
                new PushMessage(
                        "Avisos activados ✓",
                        "Desde ahora te avisamos en este teléfono.",
                        "push-test",
                        PushMessages.MEMBER_URL,
                        600,
                        Urgency.NORMAL));
        return true;
    }

    private static void requireBase64Url(String value, int expectedBytes, String what) {
        try {
            if (value == null || Base64.getUrlDecoder().decode(value).length != expectedBytes) {
                throw new InvalidPushSubscriptionException("La " + what + " de avisos no es válida");
            }
        } catch (IllegalArgumentException e) {
            throw new InvalidPushSubscriptionException("La " + what + " de avisos no es válida");
        }
    }
}
