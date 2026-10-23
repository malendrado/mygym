package com.cortesdev.mygym.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Corta con 401 una sesión cuyo usuario ya no existe, fue desactivado o cambió de rol o gimnasio
 * desde que se emitió el JWT (ver {@link UserSessionGuard}). Aplica a todos los roles, incluido el
 * super-admin: todos son filas de app_user. Va después de DemoAccessExpiryFilter, así una demo
 * vencida sigue respondiendo su 410 propio.
 */
@Component
@RequiredArgsConstructor
public class UserSessionFilter extends OncePerRequestFilter {

    public static final String CODE = "SESSION_REVOKED";

    // Las mismas rutas que SecurityConfig deja sin autenticar. Si el navegador manda un token viejo a una de ellas (el
    // interceptor del front lo adjunta siempre), revisarlo acá cortaba con 401 el inicio de sesión y las páginas públicas
    // de gente a la que ya se le había revocado el acceso, justo cuando intenta entrar de nuevo.
    private static final List<String> PUBLIC_PREFIXES = List.of("/api/auth/", "/api/public/", "/actuator/");

    private final UserSessionGuard guard;
    private final LastSeenTracker lastSeenTracker;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String servletPath = request.getServletPath() != null ? request.getServletPath() : "";
        String pathInfo = request.getPathInfo() != null ? request.getPathInfo() : "";
        String path = servletPath + pathInfo;
        if (path.isEmpty()) {
            path = request.getRequestURI();
        }
        final String normalized = path;
        return PUBLIC_PREFIXES.stream().anyMatch(normalized::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Number userId = jwtAuth.getToken().getClaim("userId");
            Number gymId = jwtAuth.getToken().getClaim("gymId");
            boolean valid = userId != null
                    && guard.isValid(
                            userId.longValue(),
                            jwtAuth.getToken().getClaimAsString("role"),
                            gymId != null ? gymId.longValue() : null,
                            jwtAuth.getToken().getIssuedAt());
            if (!valid) {
                respondRevoked(response);
                return;
            }
            lastSeenTracker.touch(userId.longValue(), jwtAuth.getToken().getClaimAsString("role"), request.getRequestURI());
        }
        filterChain.doFilter(request, response);
    }

    private void respondRevoked(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        // Armado a mano, igual que GymPauseFilter: no depende de cómo esté configurado el ObjectMapper.
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("title", "Sesión cerrada");
        problem.put("status", HttpStatus.UNAUTHORIZED.value());
        problem.put("detail", "Tu sesión ya no es válida. Vuelve a iniciar sesión.");
        problem.put("code", CODE);
        problem.put("timestamp", Instant.now().toString());
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
