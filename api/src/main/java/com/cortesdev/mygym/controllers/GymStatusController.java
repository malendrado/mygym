package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymClosure;
import com.cortesdev.mygym.models.GymSubscription;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymClosureRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.GymSubscriptionRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.security.AuthenticatedUser;
import com.cortesdev.mygym.services.exception.GymNotFoundException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Estado del gimnasio del usuario con sesion (socio, admin o profesor). Es la UNICA ruta que el filtro de pausa deja pasar
 * cuando el gimnasio esta en pausa: la app la consulta para mostrar el aviso con los datos de contacto correctos. Cuando el
 * gimnasio esta activo no devuelve ningun dato de contacto.
 */
@RestController
@RequestMapping("/api/gym-status")
@RequiredArgsConstructor
public class GymStatusController {

    public record Contact(String whatsappNumber, String email, String instagramUrl) {}

    /** `gymContact` es lo que ve un socio (como contactar a su gimnasio); `mygymContact` lo que ve el equipo del gimnasio. */
    public record GymStatusResponse(
            boolean paused,
            String gymName,
            Contact gymContact,
            Contact mygymContact,
            String closuresStamp,
            String reservationsStamp,
            String pauseReason,
            String billingStatus) {}

    private static final Logger log = LoggerFactory.getLogger(GymStatusController.class);
    private static final ZoneId GYM_ZONE = ZoneId.of("America/Santiago");

    private final GymRepository gymRepository;
    private final AppUserRepository appUserRepository;
    private final GymClosureRepository gymClosureRepository;
    private final ReservationRepository reservationRepository;
    private final GymSubscriptionRepository subscriptionRepository;

    @Value("${app.contact.whatsapp:56964641042}")
    private String mygymWhatsapp;

    @Value("${app.contact.email:contacto@mygym.cl}")
    private String mygymEmail;

    /**
     * La respuesta depende solo del gimnasio (no de quien pregunta) y cada app abierta la consulta cada ~45 s: con cientos de
     * socios eran decenas de consultas por segundo (gimnasio completo con logo, cierres y marca de reservas) contra un pool de 5
     * conexiones. Se guarda por gimnasio unos segundos; la pausa y los cierres se siguen enterando en menos de ~10 s.
     */
    @Value("${app.gym-status.cache-seconds:10}")
    private long cacheSeconds;

    private record Cached(GymStatusResponse response, long expiresAtNanos) {}

    private final java.util.concurrent.ConcurrentHashMap<Long, Cached> cache = new java.util.concurrent.ConcurrentHashMap<>();

    @GetMapping
    public GymStatusResponse status(@AuthenticationPrincipal Jwt jwt) {
        Long gymId = AuthenticatedUser.from(jwt).gymId();
        if (cacheSeconds <= 0) {
            return compute(gymId);
        }
        long now = System.nanoTime();
        Cached cached = cache.get(gymId);
        if (cached != null && now - cached.expiresAtNanos() < 0) {
            return cached.response();
        }
        GymStatusResponse fresh = compute(gymId);
        cache.put(gymId, new Cached(fresh, now + java.util.concurrent.TimeUnit.SECONDS.toNanos(cacheSeconds)));
        return fresh;
    }

    private GymStatusResponse compute(Long gymId) {
        Gym gym = gymRepository.findById(gymId).orElseThrow(() -> new GymNotFoundException(gymId));
        // Dos motivos de pausa con el mismo efecto: la apago el super-admin (MANUAL) o la suscripcion a mygym esta sin iniciar o
        // suspendida por no pago (BILLING). La app los distingue para mostrar el texto y la forma de reactivar que corresponde.
        String billingStatus = subscriptionRepository.findBillingStatusByGymId(gymId).orElse(null);
        boolean billingBlocked =
                GymSubscription.NOT_STARTED.equals(billingStatus) || GymSubscription.SUSPENDED.equals(billingStatus);
        if (gym.isActive() && !billingBlocked) {
            return new GymStatusResponse(
                    false,
                    gym.getName(),
                    null,
                    null,
                    bestEffort(() -> closuresStamp(gymId)),
                    bestEffort(() -> reservationsStamp(gymId)),
                    null,
                    null);
        }
        String pauseReason = gym.isActive() ? "BILLING" : "MANUAL";
        // Correo de contacto del gimnasio: el del PRIMER administrador que se creo (el dueño).
        String ownerEmail = appUserRepository.findByGymIdAndRole(gymId, Role.GYM_ADMIN).stream()
                .min(Comparator.comparing(AppUser::getId))
                .map(AppUser::getEmail)
                .orElse(null);
        return new GymStatusResponse(
                true,
                gym.getName(),
                new Contact(gym.getWhatsappNumber(), ownerEmail, gym.getInstagramUrl()),
                new Contact(mygymWhatsapp, mygymEmail, null),
                null,
                null,
                pauseReason,
                "BILLING".equals(pauseReason) ? billingStatus : null);
    }

    /**
     * Marca opaca del estado de los cierres vigentes o futuros del gimnasio (id, fechas y levantamiento anticipado de cada
     * uno): cambia al crear, editar o levantar un cierre. La app la compara en cada consulta y, si cambió, recarga sus
     * clases sin necesidad de otra llamada. Es una sola consulta por el indice (gym_id, start_date, end_date) sobre unas
     * pocas filas, y no revela datos de los cierres (solo un hash).
     */
    /**
     * Marca opaca de las reservas vigentes del gimnasio (cuantas hay y cual es la mas nueva): cambia cuando alguien reserva o
     * cancela, o sea cuando cambia el cupo de una clase. La app la compara en cada consulta y, si cambió, recarga los cupos.
     * Una consulta por indice; no revela quien reservo ni que clase.
     */
    private String reservationsStamp(Long gymId) {
        ReservationRepository.BookedStamp booked = reservationRepository.bookedStamp(gymId, LocalDate.now(GYM_ZONE));
        return Integer.toHexString((booked.getTotal() + ":" + booked.getLastId()).hashCode());
    }

    /** Las marcas son un extra: si una consulta fallara, el estado (y sobre todo el aviso de pausa) no puede caerse con ella. */
    private static String bestEffort(Supplier<String> stamp) {
        try {
            return stamp.get();
        } catch (RuntimeException e) {
            log.warn("No se pudo calcular una marca de cambios de gym-status: {}", e.getMessage());
            return null;
        }
    }

    private String closuresStamp(Long gymId) {
        String joined = gymClosureRepository.findByGymIdAndEndDateGreaterThanEqual(gymId, LocalDate.now(GYM_ZONE)).stream()
                .sorted(Comparator.comparing(GymClosure::getId))
                .map(c -> c.getId() + ":" + c.getStartDate() + ":" + c.getEndDate() + ":" + c.getLiftedAt())
                .collect(Collectors.joining("|"));
        return Integer.toHexString(joined.hashCode());
    }
}
