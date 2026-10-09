package com.cortesdev.mygym.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Límite de intentos por IP para los endpoints de autenticación — auditoría de seguridad
 * 2026-10-09 encontró que ninguno tenía rate limiting, dejando login/registro/forgot-password
 * abiertos a fuerza bruta y credential stuffing sin ningún freno.
 *
 * <p>Implementación en memoria (ventana fija por IP+ruta) — suficiente para un único proceso
 * (mygym-api corre hoy en una sola instancia de Railway), pero NO sirve si algún día se escala a
 * más de una instancia (cada una tendría su propio contador, multiplicando el límite real por la
 * cantidad de instancias). Si eso cambia, reemplazar por un store compartido (Redis) antes de
 * confiar en este filtro.
 */
@Component
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_ATTEMPTS = 10;
    private static final long WINDOW_MILLIS = Duration.ofMinutes(15).toMillis();

    private static final Set<String> LIMITED_PATHS =
            Set.of("/api/auth/google", "/api/public/auth/login", "/api/public/auth/register", "/api/public/auth/forgot-password");

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Window> attemptsByKey = new ConcurrentHashMap<>();

    private static final class Window {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long startedAt;

        private Window(long now) {
            this.startedAt = now;
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = clientIp(request) + "|" + request.getRequestURI();
        long now = System.currentTimeMillis();
        Window window = attemptsByKey.computeIfAbsent(key, k -> new Window(now));

        synchronized (window) {
            if (now - window.startedAt > WINDOW_MILLIS) {
                window.startedAt = now;
                window.count.set(0);
            }
            if (window.count.incrementAndGet() > MAX_ATTEMPTS) {
                respondTooManyAttempts(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    // Railway (y cualquier PaaS detrás de un proxy) entrega la IP real del cliente en
    // X-Forwarded-For, no en request.getRemoteAddr() (que sería la IP del proxy interno).
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void respondTooManyAttempts(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos. Intenta de nuevo en unos minutos.");
        problem.setTitle("Demasiados intentos");
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
