package com.cortesdev.mygym.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Límite de intentos para los endpoints de autenticación y los públicos que escriben en la base
 * (auditoría de seguridad 2026-10-09: ninguno tenía rate limiting; auditoría 2026-10-17: el límite
 * se evadía mandando un X-Forwarded-For falso, el mapa de ventanas nunca se purgaba y quedaban
 * rutas públicas sin cubrir).
 *
 * <p>Implementación en memoria (ventana fija por clave+ruta) — suficiente para un único proceso
 * (mygym-api corre hoy en una sola instancia de Railway), pero NO sirve si algún día se escala a
 * más de una instancia (cada una tendría su propio contador, multiplicando el límite real por la
 * cantidad de instancias). Si eso cambia, reemplazar por un store compartido (Redis) antes de
 * confiar en este filtro.
 */
@Component
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = Duration.ofMinutes(15).toMillis();
    // Cada tanto se tiran las ventanas vencidas; y si aun así el mapa explota (miles de claves distintas),
    // se vacía entero: peor caso, a todos se les reinicia el contador, nunca se agota la memoria.
    private static final long PURGE_EVERY_MILLIS = Duration.ofMinutes(1).toMillis();
    private static final int MAX_KEYS = 50_000;

    /** Cuántos intentos por ventana, y si se cuenta por IP o por usuario autenticado. */
    private record Rule(int maxAttempts, boolean perUser) {}

    private static final Rule STRICT = new Rule(10, false);
    private static final Rule MEDIUM = new Rule(20, false);

    // Rutas exactas (POST). El resto de las rutas con variable en el medio van en ruleFor.
    private static final Map<String, Rule> EXACT_RULES = Map.of(
            "/api/auth/google", STRICT,
            "/api/public/auth/login", STRICT,
            "/api/public/auth/register", STRICT,
            "/api/public/auth/forgot-password", STRICT,
            "/api/public/auth/activate", MEDIUM,
            // Cada llamada inserta una fila que nadie purga.
            "/api/public/tv/pairing", MEDIUM,
            // Detrás del wifi de un gimnasio muchos socios comparten IP: más holgado.
            "/api/public/analytics/visit", new Rule(120, false),
            // Se cuenta por socio (no por IP: todo un gimnasio comparte wifi). Un socio legítimo escanea 1-2 veces.
            "/api/me/checkin", new Rule(15, true));

    // POST /api/public/gyms/{slug}/join — llena el cupo de socios del gimnasio con cuentas de Google.
    private static final Pattern JOIN_PATH = Pattern.compile("^/api/public/gyms/[^/]+/join$");
    private static final Rule JOIN_RULE = MEDIUM;

    // GET /api/public/tv/pairing/{code} — la TV lo consulta cada 3 s mientras espera el emparejamiento (~200 veces en los 10 min de
    // vida del código); sin tope sería una vía para probar códigos. Holgado porque varias TV de un gimnasio comparten IP y se
    // pueden emparejar a la vez (el código de 6 caracteres sigue siendo inviable de adivinar a este ritmo).
    private static final Pattern PAIRING_STATUS_PATH = Pattern.compile("^/api/public/tv/pairing/[^/]+$");
    private static final Rule PAIRING_STATUS_RULE = new Rule(1200, false);

    // POST /api/public/tv/screens/{token}/checkin-code — cada llamada inserta una fila; la TV lo pide cada ~30 s.
    private static final Pattern TV_CHECKIN_CODE_PATH = Pattern.compile("^/api/public/tv/screens/[^/]+/checkin-code$");
    private static final Rule TV_CHECKIN_CODE_RULE = new Rule(300, false);

    // GET /api/public/gyms/{slug}/photos y /api/public/tv/screens/{token}/photos: cada llamada lee hasta 8 fotos en base64 (varios MB)
    // de la base y ocupa una conexion mientras tanto. El navegador las guarda 5-10 min; esto frena a quien las pida en bucle.
    // Holgado porque un gimnasio comparte una sola IP (el wifi) entre todos sus socios.
    private static final Pattern PUBLIC_PHOTOS_PATH = Pattern.compile("^/api/public/gyms/[^/]+/photos$");
    private static final Rule PUBLIC_PHOTOS_RULE = new Rule(240, false);
    private static final Pattern TV_PHOTOS_PATH = Pattern.compile("^/api/public/tv/screens/[^/]+/photos$");
    private static final Rule TV_PHOTOS_RULE = new Rule(60, false);

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Window> attemptsByKey = new ConcurrentHashMap<>();
    private volatile long lastPurgeAt = System.currentTimeMillis();

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
        // Ruta ya decodificada y normalizada por el servlet: getRequestURI() devuelve el texto crudo y
        // "/api/public/auth/%6Cogin" llegaba al controlador pero no coincidía con la lista.
        String path = normalizedPath(request);
        Rule rule = ruleFor(request.getMethod(), path);
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String subject = rule.perUser() ? currentUser() : clientIp(request);
        if (subject == null) {
            // Ruta por usuario sin sesión: la rechaza la autorización más adelante, acá no hay a quién contar.
            filterChain.doFilter(request, response);
            return;
        }

        long now = System.currentTimeMillis();
        purgeIfDue(now);
        String key = subject + "|" + bucketOf(path);
        Window window = attemptsByKey.computeIfAbsent(key, k -> new Window(now));

        synchronized (window) {
            if (now - window.startedAt > WINDOW_MILLIS) {
                window.startedAt = now;
                window.count.set(0);
            }
            if (window.count.incrementAndGet() > rule.maxAttempts()) {
                respondTooManyAttempts(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private static String normalizedPath(HttpServletRequest request) {
        String servletPath = request.getServletPath() != null ? request.getServletPath() : "";
        String pathInfo = request.getPathInfo() != null ? request.getPathInfo() : "";
        String path = servletPath + pathInfo;
        if (path.isEmpty()) {
            path = request.getRequestURI();
        }
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    /**
     * Una sola cuenta por ruta, no por texto de la URL: en las rutas con una parte variable (código, token, slug) contar por
     * URL completa dejaba al atacante rotar esa parte y empezar de cero cada vez.
     */
    private static String bucketOf(String path) {
        if (PAIRING_STATUS_PATH.matcher(path).matches()) {
            return "/api/public/tv/pairing/{code}";
        }
        if (TV_CHECKIN_CODE_PATH.matcher(path).matches()) {
            return "/api/public/tv/screens/{token}/checkin-code";
        }
        if (JOIN_PATH.matcher(path).matches()) {
            return "/api/public/gyms/{slug}/join";
        }
        if (PUBLIC_PHOTOS_PATH.matcher(path).matches()) {
            return "/api/public/gyms/{slug}/photos";
        }
        if (TV_PHOTOS_PATH.matcher(path).matches()) {
            return "/api/public/tv/screens/{token}/photos";
        }
        return path;
    }

    private static Rule ruleFor(String method, String path) {
        if ("GET".equalsIgnoreCase(method)) {
            if (PAIRING_STATUS_PATH.matcher(path).matches()) {
                return PAIRING_STATUS_RULE;
            }
            if (PUBLIC_PHOTOS_PATH.matcher(path).matches()) {
                return PUBLIC_PHOTOS_RULE;
            }
            return TV_PHOTOS_PATH.matcher(path).matches() ? TV_PHOTOS_RULE : null;
        }
        if (!"POST".equalsIgnoreCase(method)) {
            return null;
        }
        Rule exact = EXACT_RULES.get(path);
        if (exact != null) {
            return exact;
        }
        if (TV_CHECKIN_CODE_PATH.matcher(path).matches()) {
            return TV_CHECKIN_CODE_RULE;
        }
        return JOIN_PATH.matcher(path).matches() ? JOIN_RULE : null;
    }

    private static String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Number userId = jwtAuth.getToken().getClaim("userId");
            if (userId != null) {
                return "user:" + userId.longValue();
            }
        }
        return null;
    }

    private void purgeIfDue(long now) {
        if (now - lastPurgeAt < PURGE_EVERY_MILLIS && attemptsByKey.size() < MAX_KEYS) {
            return;
        }
        lastPurgeAt = now;
        attemptsByKey.entrySet().removeIf(entry -> now - entry.getValue().startedAt > WINDOW_MILLIS);
        if (attemptsByKey.size() >= MAX_KEYS) {
            attemptsByKey.clear();
        }
    }

    // Railway (y cualquier PaaS detrás de un proxy) entrega la IP real del cliente en X-Forwarded-For,
    // no en request.getRemoteAddr() (que sería la IP del proxy interno). Cada proxy AGREGA la IP de quien
    // le habló al final de la lista, así que la ÚNICA entrada confiable es la última: las anteriores las
    // escribe el propio cliente y rotándolas se evadía el límite por completo. Si el proxy de borde
    // sobrescribe el header en vez de agregarlo, hay una sola entrada y da igual.
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            String last = parts[parts.length - 1].trim();
            if (!last.isEmpty()) {
                return last;
            }
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
