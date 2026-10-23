package com.cortesdev.mygym.security;

import com.cortesdev.mygym.repositories.AppUserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Anota cuándo un administrador de gimnasio hizo algo en la app por última vez (app_user.last_seen_at). No toca la base en cada
 * petición: guarda en memoria cuándo escribió por última vez a cada admin y escribe como mucho una vez por hora; además el UPDATE es
 * condicional, así que un reinicio o una segunda instancia tampoco generan escrituras de más. Las consultas automáticas de fondo
 * (el estado del gimnasio que cada app abierta pregunta cada ~45 s) no cuentan: sin esa excepción una pestaña olvidada abierta
 * pareciera actividad. Un fallo al anotar nunca rompe la petición del usuario.
 */
@Component
@RequiredArgsConstructor
public class LastSeenTracker {

    private static final Logger log = LoggerFactory.getLogger(LastSeenTracker.class);

    static final Duration INTERVAL = Duration.ofHours(1);
    private static final Set<String> TRACKED_ROLES = Set.of("GYM_ADMIN");
    private static final Set<String> BACKGROUND_PATHS = Set.of("/api/gym-status");

    private final AppUserRepository appUserRepository;
    private final Map<Long, Long> lastWrite = new ConcurrentHashMap<>();

    public void touch(Long userId, String role, String path) {
        if (userId == null || !TRACKED_ROLES.contains(role) || BACKGROUND_PATHS.contains(path)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastWrite.get(userId);
        if (last != null && now - last < INTERVAL.toMillis()) {
            return;
        }
        // Se marca antes de escribir: si la base falla no se reintenta en cada petición, sino en la próxima hora.
        lastWrite.put(userId, now);
        try {
            appUserRepository.touchLastSeen(userId, Instant.ofEpochMilli(now), Instant.ofEpochMilli(now - INTERVAL.toMillis()));
        } catch (RuntimeException e) {
            log.warn("No se pudo anotar la última actividad del usuario {}: {}", userId, e.getMessage());
        }
    }
}
