package com.cortesdev.mygym.security;

import com.cortesdev.mygym.models.GymSubscription;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.GymSubscriptionRepository;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Responde "¿este gimnasio esta en pausa y por que?" para el control de cada peticion (GymPauseFilter). Hay dos motivos:
 * {@link Reason#MANUAL} (el super-admin lo apago: Gym.active = false) y {@link Reason#BILLING} (su suscripcion a mygym esta
 * sin iniciar o suspendida por no pago). El efecto es el mismo; cambian los textos y la forma de reactivar.
 *
 * <p>Guarda el resultado 30 segundos en memoria: sin eso cada llamada de la app sumaria consultas a la base (el pool de
 * conexiones es de 5). Al cambiar el estado desde el panel o por un pago se invalida al instante ({@link #invalidate}); en otra
 * instancia tardaria como maximo 30 s (hoy hay una sola).
 */
@Component
@RequiredArgsConstructor
public class GymPauseGuard {

    public enum Reason {
        NONE,
        MANUAL,
        BILLING
    }

    private static final long TTL_MILLIS = Duration.ofSeconds(30).toMillis();

    private record Entry(Reason reason, long expiresAt) {}

    private final GymRepository gymRepository;
    private final GymSubscriptionRepository subscriptionRepository;
    private final Map<Long, Entry> cache = new ConcurrentHashMap<>();

    public Reason reason(Long gymId) {
        long now = System.currentTimeMillis();
        Entry entry = cache.get(gymId);
        if (entry != null && entry.expiresAt() > now) {
            return entry.reason();
        }
        Reason reason = compute(gymId);
        cache.put(gymId, new Entry(reason, now + TTL_MILLIS));
        return reason;
    }

    public boolean isPaused(Long gymId) {
        return reason(gymId) != Reason.NONE;
    }

    public void invalidate(Long gymId) {
        cache.remove(gymId);
    }

    private Reason compute(Long gymId) {
        if (!gymRepository.existsByIdAndActiveTrue(gymId)) {
            return Reason.MANUAL;
        }
        // Un gimnasio sin fila de suscripcion (anterior a este modulo o de pruebas) no se cobra: nunca se pausa por esto.
        String status = subscriptionRepository.findBillingStatusByGymId(gymId).orElse(null);
        boolean blocked = GymSubscription.NOT_STARTED.equals(status) || GymSubscription.SUSPENDED.equals(status);
        return blocked ? Reason.BILLING : Reason.NONE;
    }
}
