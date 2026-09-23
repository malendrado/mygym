package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.repositories.AppUserRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Acceso de solo-lectura a la demo comercial (Role.DEMO_ADMIN): dura 48 horas desde que el
 * super-admin lo otorga (createdAt), sin reactivación — pedido explícito del usuario: "si quiere
 * volver al demo el super admin lo ingresa de nuevo". Usado desde dos lugares que necesitan la
 * MISMA lógica de expirar+borrar: AuthService (primer intento de login después de vencido) y
 * DemoAccessExpiryFilter (una sesión ya logueada que sigue viva más allá de las 48h — el JWT en
 * sí dura 30 días, así que sin este chequeo por-request el límite de 48h no sería real).
 */
@Service
@RequiredArgsConstructor
public class DemoAccessService {

    public static final Duration DEMO_ACCESS_TTL = Duration.ofHours(48);

    private final AppUserRepository appUserRepository;

    /** Si el acceso ya venció, lo borra ahí mismo (no hay estado "vencido", solo existe o no). */
    public boolean expireIfNeeded(AppUser user) {
        if (user.getRole() != Role.DEMO_ADMIN) {
            return false;
        }
        boolean expired = Instant.now().isAfter(user.getCreatedAt().plus(DEMO_ACCESS_TTL));
        if (expired) {
            appUserRepository.delete(user);
        }
        return expired;
    }
}
