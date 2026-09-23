package com.cortesdev.mygym.security;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.services.DemoAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
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
 * El JWT de un DEMO_ADMIN vive 30 días (JwtService.EXPIRATION_DAYS) — sin este filtro, el límite
 * de 48h de DemoAccessService solo se respetaría en el momento del login (AuthService), y
 * cualquiera que ya haya entrado dentro de esas 48h se quedaría con acceso funcional por semanas.
 * Corre en cada request autenticado como DEMO_ADMIN y aplica el mismo corte ahí también.
 */
@Component
@RequiredArgsConstructor
public class DemoAccessExpiryFilter extends OncePerRequestFilter {

    private final AppUserRepository appUserRepository;
    private final DemoAccessService demoAccessService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth
                && Role.valueOf(jwtAuth.getToken().getClaimAsString("role")) == Role.DEMO_ADMIN) {
            Number userIdClaim = jwtAuth.getToken().getClaim("userId");
            Optional<AppUser> user = appUserRepository.findById(userIdClaim.longValue());
            boolean expired = user.isEmpty() || demoAccessService.expireIfNeeded(user.get());
            if (expired) {
                respondExpired(response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private void respondExpired(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.GONE.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.GONE, "Tu acceso a la demo venció.");
        problem.setTitle("Demo vencida");
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
