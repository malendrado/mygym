package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.PushSubscription;
import com.cortesdev.mygym.repositories.PushSubscriptionRepository;
import com.cortesdev.mygym.services.PushNotificationService.Delivery;
import com.cortesdev.mygym.services.PushNotificationService.PushRequested;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envia los avisos push pedidos con {@link PushNotificationService}. Corre despues del commit y en el pool
 * `pushExecutor` (chico y dedicado): son llamadas HTTP salientes y no deben competir con las requests ni ocupar el pool
 * de conexiones. Best-effort: un error aqui solo se registra.
 */
@Component
@RequiredArgsConstructor
public class PushDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PushDispatcher.class);
    /** Una suscripcion que falla tantas veces seguidas (sin 404/410 claro) se da por muerta y se borra. */
    private static final int MAX_CONSECUTIVE_FAILURES = 10;

    private final PushSubscriptionRepository subscriptionRepository;
    private final PushSender sender;

    @Async("pushExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPushRequested(PushRequested event) {
        try {
            if (!sender.isConfigured()) {
                return;
            }
            Delivery delivery = event.delivery().get();
            if (delivery == null || delivery.userIds().isEmpty()) {
                return;
            }
            List<PushSubscription> subscriptions = subscriptionRepository.findByUserIdIn(delivery.userIds());
            for (PushSubscription subscription : subscriptions) {
                deliver(subscription, delivery.message());
            }
        } catch (RuntimeException e) {
            log.warn("No se pudo enviar el aviso push: {}", e.getClass().getSimpleName());
        }
    }

    private void deliver(PushSubscription subscription, PushMessage message) {
        PushSender.Result result = sender.send(subscription, message);
        switch (result) {
            case SENT -> {
                subscription.setLastSuccessAt(Instant.now());
                subscription.setFailureCount(0);
                subscriptionRepository.save(subscription);
            }
            case GONE -> subscriptionRepository.delete(subscription);
            case FAILED -> {
                subscription.setFailureCount(subscription.getFailureCount() + 1);
                if (subscription.getFailureCount() >= MAX_CONSECUTIVE_FAILURES) {
                    subscriptionRepository.delete(subscription);
                } else {
                    subscriptionRepository.save(subscription);
                }
            }
            case SKIPPED -> {
                // nada que registrar: no se intento
            }
        }
    }
}
