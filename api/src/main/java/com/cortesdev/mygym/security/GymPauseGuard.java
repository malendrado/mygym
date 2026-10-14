package com.cortesdev.mygym.security;

import com.cortesdev.mygym.repositories.GymRepository;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Responde "¿este gimnasio esta en pausa (Gym.active = false)?" para el control de cada peticion (GymPauseFilter). Guarda el
 * resultado 30 segundos en memoria: sin eso cada llamada de la app sumaria una consulta a la base (el pool de conexiones
 * es de 5). Al cambiar el estado desde el panel se invalida al instante ({@link #invalidate}), asi la pausa y la
 * reactivacion se notan de inmediato; en otra instancia tardaria como maximo 30 s (hoy hay una sola).
 */
@Component
@RequiredArgsConstructor
public class GymPauseGuard {

    private static final long TTL_MILLIS = Duration.ofSeconds(30).toMillis();

    private record Entry(boolean paused, long expiresAt) {}

    private final GymRepository gymRepository;
    private final Map<Long, Entry> cache = new ConcurrentHashMap<>();

    public boolean isPaused(Long gymId) {
        long now = System.currentTimeMillis();
        Entry entry = cache.get(gymId);
        if (entry != null && entry.expiresAt() > now) {
            return entry.paused();
        }
        boolean paused = !gymRepository.existsByIdAndActiveTrue(gymId);
        cache.put(gymId, new Entry(paused, now + TTL_MILLIS));
        return paused;
    }

    public void invalidate(Long gymId) {
        cache.remove(gymId);
    }
}
