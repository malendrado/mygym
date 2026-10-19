package com.cortesdev.mygym.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gimnasio en pausa (Gym.active = false): socios, administradores y profesores de ese gimnasio siguen pudiendo iniciar
 * sesion (la app les muestra un aviso amable con los datos de contacto) pero la API no les deja hacer nada mas que
 * consultar el estado ({@code /api/gym-status}). El super-admin no tiene gimnasio, asi que nunca queda afectado: puede
 * reactivarlo.
 *
 * <p>Esto va en el servidor y no solo en la vista: una app con version vieja en cache, o una llamada directa a la API,
 * tampoco debe poder reservar ni editar.
 */
@Component
@RequiredArgsConstructor
public class GymPauseFilter extends OncePerRequestFilter {

    public static final String CODE = "GYM_PAUSED";
    private static final Set<String> GYM_ROLES = Set.of("MEMBER", "GYM_ADMIN", "PROFESOR", "DEMO_ADMIN");
    private static final String STATUS_PATH = "/api/gym-status";
    /** Lo unico que un administrador puede usar estando suspendido por no pago: ver su suscripcion y pagarla. */
    private static final String SUBSCRIPTION_PREFIX = "/api/gym-admin/subscription";

    private final GymPauseGuard guard;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth
                && GYM_ROLES.contains(jwtAuth.getToken().getClaimAsString("role"))
                && !STATUS_PATH.equals(request.getRequestURI())) {
            Number gymId = jwtAuth.getToken().getClaim("gymId");
            if (gymId != null) {
                GymPauseGuard.Reason reason = guard.reason(gymId.longValue());
                boolean payingOwnSubscription = reason == GymPauseGuard.Reason.BILLING
                        && "GYM_ADMIN".equals(jwtAuth.getToken().getClaimAsString("role"))
                        && (request.getRequestURI().equals(SUBSCRIPTION_PREFIX)
                                || request.getRequestURI().startsWith(SUBSCRIPTION_PREFIX + "/"));
                if (reason != GymPauseGuard.Reason.NONE && !payingOwnSubscription) {
                    respondPaused(response, reason);
                    return;
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private void respondPaused(HttpServletResponse response, GymPauseGuard.Reason reason) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        // Mismo formato que un ProblemDetail, armado a mano: asi no depende de como este configurado el ObjectMapper.
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("title", "Gimnasio en pausa");
        problem.put("status", HttpStatus.FORBIDDEN.value());
        problem.put("detail", "Este gimnasio está en pausa por ahora.");
        problem.put("code", CODE);
        problem.put("reason", reason.name());
        problem.put("timestamp", Instant.now().toString());
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
