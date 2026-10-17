package com.cortesdev.mygym.security;

import com.cortesdev.mygym.repositories.AppUserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Responde "¿esta sesión (JWT) sigue siendo válida?" para el control de cada petición
 * ({@link UserSessionFilter}). El JWT vive 30 días y trae rol y gymId firmados: sin esta revisión,
 * quitar o desactivar a un admin, profesor o socio no le cortaba el acceso hasta que el token
 * venciera. Se compara contra la fila actual del usuario: que exista, esté activa y conserve el
 * mismo rol y gimnasio con los que se emitió el token.
 *
 * <p>Guarda cada respuesta 30 segundos en memoria (mismo criterio que {@link GymPauseGuard}: el
 * pool de conexiones es de 5 y no se puede sumar una consulta por cada llamada de la app). Quitar un
 * acceso se nota como máximo 30 s después; {@link #invalidate} lo corta al instante si algún flujo lo pide.
 */
@Component
@RequiredArgsConstructor
public class UserSessionGuard {

    private static final long TTL_MILLIS = Duration.ofSeconds(30).toMillis();

    private record State(boolean exists, boolean active, String role, Long gymId, Instant sessionsValidAfter, long expiresAt) {}

    private final AppUserRepository appUserRepository;
    private final Map<Long, State> cache = new ConcurrentHashMap<>();

    public boolean isValid(Long userId, String role, Long gymId) {
        return isValid(userId, role, gymId, null);
    }

    /** {@code issuedAt}: cuándo se emitió el token (claim iat); null si no se conoce, y entonces no se compara. */
    public boolean isValid(Long userId, String role, Long gymId, Instant issuedAt) {
        long now = System.currentTimeMillis();
        State state = cache.get(userId);
        if (state == null || state.expiresAt() <= now) {
            state = load(userId, now);
            cache.put(userId, state);
        }
        // El iat del JWT va en segundos: la marca se guarda truncada a segundos para que el token recién emitido en el
        // mismo segundo del cambio de clave no quede invalidado (solo se rechaza lo estrictamente anterior).
        boolean issuedBeforeInvalidation = issuedAt != null
                && state.sessionsValidAfter() != null
                && issuedAt.isBefore(state.sessionsValidAfter());
        return state.exists()
                && state.active()
                && !issuedBeforeInvalidation
                && Objects.equals(state.role(), role)
                && Objects.equals(state.gymId(), gymId);
    }

    public void invalidate(Long userId) {
        cache.remove(userId);
    }

    private State load(Long userId, long now) {
        return appUserRepository
                .findSessionViewById(userId)
                .map(view -> new State(
                        true,
                        Boolean.TRUE.equals(view.getActive()),
                        view.getRole() != null ? view.getRole().name() : null,
                        view.getGymId(),
                        view.getSessionsValidAfter(),
                        now + TTL_MILLIS))
                .orElseGet(() -> new State(false, false, null, null, null, now + TTL_MILLIS));
    }
}
