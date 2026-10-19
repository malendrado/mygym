package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.GymLimitsResponse;
import com.cortesdev.mygym.models.dto.GymLimitsResponse.Usage;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.GymSubscriptionRepository;
import com.cortesdev.mygym.services.exception.GymLimitReachedException;
import com.cortesdev.mygym.services.exception.GymLimitReachedException.Kind;
import com.cortesdev.mygym.services.exception.GymNotFoundException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Limites de un gimnasio: socios (cupo que fija el plan o el super-admin en Gym.maxUsers), profesores y administradores
 * (los que fija su plan de mygym; 10 y 5 si no tiene plan). "Socio" es cualquier cuenta MEMBER del gimnasio, incluidos los invitados que todavia no activan su
 * cuenta y los inactivos: si no, el admin podria invitar sin limite.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GymCapacityService {

    public static final int MAX_PROFESORES = 10;
    public static final int MAX_ADMINS = 5;

    private static final Logger log = LoggerFactory.getLogger(GymCapacityService.class);
    private static final ZoneId GYM_ZONE = ZoneId.of("America/Santiago");

    private final AppUserRepository appUserRepository;
    private final GymRepository gymRepository;
    private final GymSubscriptionRepository subscriptionRepository;
    private final MemberLifecycleEmailService emailService;

    // Un correo al dia por gimnasio como maximo cuando alguien no pudo registrarse por falta de cupo. En memoria: la API
    // corre en una sola instancia (igual que los crons); si se reinicia, a lo sumo se repite un aviso ese dia.
    private final Map<Long, LocalDate> lastLimitNotice = new ConcurrentHashMap<>();

    public GymLimitsResponse limits(Long gymId) {
        int maxUsers = gymRepository.findMaxUsersById(gymId).orElseThrow(() -> new GymNotFoundException(gymId));
        return new GymLimitsResponse(
                Usage.of(appUserRepository.countByGymIdAndRole(gymId, Role.MEMBER), maxUsers),
                Usage.of(appUserRepository.countByGymIdAndRole(gymId, Role.PROFESOR), maxProfesores(gymId)),
                Usage.of(appUserRepository.countByGymIdAndRole(gymId, Role.GYM_ADMIN), maxAdmins(gymId)));
    }

    /** Cuantos socios mas caben ahora mismo (0 si ya se llego o se paso del cupo). */
    public int freeMemberSlots(Gym gym) {
        long used = appUserRepository.countByGymIdAndRole(gym.getId(), Role.MEMBER);
        return (int) Math.max(0, gym.getMaxUsers() - used);
    }

    /** Lanza si no caben `toAdd` socios mas. Para altas hechas por el propio equipo del gimnasio. */
    @Transactional
    public void assertMemberCapacity(Gym gym, int toAdd) {
        lockGymForCount(gym.getId());
        if (freeMemberSlots(gym) < toAdd) {
            throw new GymLimitReachedException(
                    Kind.MEMBERS,
                    "Tu gimnasio llegó al límite de " + gym.getMaxUsers() + " socios. Escríbenos a contacto@mygym.cl para ampliar tu cupo.");
        }
    }

    /**
     * Alta hecha por la propia persona (Google o email): si no hay cupo se le dice con buena onda y se avisa al
     * administrador (maximo un correo al dia).
     */
    @Transactional
    public void assertSelfRegistrationAllowed(Gym gym) {
        lockGymForCount(gym.getId());
        if (freeMemberSlots(gym) > 0) {
            return;
        }
        notifyAdminsOncePerDay(gym);
        throw new GymLimitReachedException(
                Kind.MEMBERS,
                "Por ahora " + gym.getName() + " no tiene cupos disponibles. Avísale a tu gimnasio para que te habilite un lugar.");
    }

    @Transactional
    public void assertProfesorCapacity(Long gymId) {
        lockGymForCount(gymId);
        int max = maxProfesores(gymId);
        if (appUserRepository.countByGymIdAndRole(gymId, Role.PROFESOR) >= max) {
            throw new GymLimitReachedException(
                    Kind.PROFESORES, "Llegaste al máximo de " + max + " profesores por gimnasio.");
        }
    }

    @Transactional
    public void assertAdminCapacity(Long gymId) {
        lockGymForCount(gymId);
        int max = maxAdmins(gymId);
        if (appUserRepository.countByGymIdAndRole(gymId, Role.GYM_ADMIN) >= max) {
            throw new GymLimitReachedException(
                    Kind.ADMINS, "Llegaste al máximo de " + max + " administradores por gimnasio.");
        }
    }

    private int maxProfesores(Long gymId) {
        return subscriptionRepository.findMaxProfesoresByGymId(gymId).orElse(MAX_PROFESORES);
    }

    private int maxAdmins(Long gymId) {
        return subscriptionRepository.findMaxAdminsByGymId(gymId).orElse(MAX_ADMINS);
    }

    /**
     * Serializa los chequeos de cupo de un mismo gimnasio: sin esto, varias altas en paralelo (doble clic, dos administradores,
     * un reintento) contaban todas "queda 1 cupo" y se pasaban del limite. El bloqueo dura hasta el fin de la transaccion del
     * llamador, que es la que inserta la cuenta. El resultado se ignora: si el gimnasio no existe, el llamador ya lo trata.
     */
    private void lockGymForCount(Long gymId) {
        gymRepository.lockForCapacity(gymId);
    }

    private void notifyAdminsOncePerDay(Gym gym) {
        LocalDate today = LocalDate.now(GYM_ZONE);
        if (today.equals(lastLimitNotice.put(gym.getId(), today))) {
            return; // ya se aviso hoy
        }
        try {
            List<String> adminEmails = appUserRepository.findByGymIdAndRole(gym.getId(), Role.GYM_ADMIN).stream()
                    .filter(AppUser::isActive)
                    .map(AppUser::getEmail)
                    .toList();
            if (!adminEmails.isEmpty()) {
                emailService.sendMemberLimitReachedAdmin(gym, adminEmails, gym.getMaxUsers());
            }
        } catch (RuntimeException e) {
            log.warn("No se pudo avisar el límite de socios al gimnasio {}: {}", gym.getId(), e.getMessage());
        }
    }
}
