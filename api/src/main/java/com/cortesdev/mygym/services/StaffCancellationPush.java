package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.ClassWaitlistRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.services.PushNotificationService.Delivery;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Cuando un socio cancela su reserva, avisa por push al administrador y a los profesores del gimnasio (pedido del
 * usuario 2026-10-13). Sale al instante: apenas se confirma la cancelacion. Los avisos de una misma clase comparten tag,
 * asi que el telefono deja uno solo, actualizado ("3 cancelaciones…").
 *
 * <p>Va a TODOS los profesores del gimnasio, no solo al de esa clase: el bloque guarda el profesor como texto
 * (GymBlock.instructorName), sin vinculo a un usuario PROFESOR.
 */
@Component
@RequiredArgsConstructor
public class StaffCancellationPush {

    private static final ZoneId GYM_ZONE = ZoneId.of("America/Santiago");
    /** Ventana para contar "otras cancelaciones recientes" de la misma clase en el texto del aviso. */
    private static final Duration RECENT_WINDOW = Duration.ofHours(2);

    private final PushNotificationService pushService;
    private final AppUserRepository appUserRepository;
    private final ReservationRepository reservationRepository;
    private final ClassWaitlistRepository waitlistRepository;

    /** Llamar solo cuando el SOCIO cancela por su cuenta (no el admin por el, ni un cierre, ni las clases fijas). */
    public void memberCancelled(GymBlock block, LocalDate classDate, AppUser member) {
        ZonedDateTime start = classDate.atTime(block.getStartTime()).atZone(GYM_ZONE);
        if (!ZonedDateTime.now(GYM_ZONE).isBefore(start)) {
            return; // la clase ya empezo: avisar una cancelacion ya no sirve para actuar
        }
        String memberName = member.getName() == null || member.getName().isBlank() ? "Un socio" : member.getName().trim();
        // Destinatarios y cupos se calculan DESPUES del commit (ya con esta cancelacion contada).
        pushService.notifyLater(() -> {
            List<Long> staff = staffOf(block.getGymId());
            if (staff.isEmpty()) {
                return null;
            }
            int taken = reservationRepository.countByGymBlockIdAndClassDateAndStatus(
                    block.getId(), classDate, ReservationStatus.BOOKED);
            int free = Math.max(0, block.getCapacity() - taken);
            int waiting = waitlistRepository
                    .findByGymBlockIdAndClassDateOrderByCreatedAtAsc(block.getId(), classDate)
                    .size();
            int recent = Math.max(
                    1,
                    reservationRepository.countByGymBlockIdAndClassDateAndStatusAndCancelledByClosureIdIsNullAndUpdatedAtAfter(
                            block.getId(), classDate, ReservationStatus.CANCELLED, Instant.now().minus(RECENT_WINDOW)));
            return new Delivery(staff, PushMessages.memberCancelled(block, classDate, memberName, recent, free, waiting));
        });
    }

    private List<Long> staffOf(Long gymId) {
        return java.util.stream.Stream.concat(
                        appUserRepository.findByGymIdAndRole(gymId, Role.GYM_ADMIN).stream(),
                        appUserRepository.findByGymIdAndRole(gymId, Role.PROFESOR).stream())
                .filter(AppUser::isActive)
                .map(AppUser::getId)
                .toList();
    }
}
