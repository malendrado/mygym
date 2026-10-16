package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.GymClosure;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Avisos push al socio cuando el equipo del gimnasio le cambia algo SIN que el socio haga nada: le cancelan una reserva,
 * cambian o cancelan su clase, le quitan el plan o se levanta un cierre. Antes todos esos cambios eran silenciosos.
 *
 * <p>Reglas comunes: un gimnasio en pausa no manda nada, y un fallo del aviso NUNCA debe tumbar la operacion de fondo
 * (todo es "mejor esfuerzo"). El envio real ocurre despues de confirmar la transaccion (ver PushNotificationService).
 */
@Service
@RequiredArgsConstructor
public class MemberChangeNotifier {

    private static final Logger log = LoggerFactory.getLogger(MemberChangeNotifier.class);
    private static final ZoneId GYM_ZONE = ZoneId.of("America/Santiago");

    private final PushNotificationService pushService;
    private final ReservationRepository reservationRepository;
    private final AppUserRepository appUserRepository;

    /** Se levantó un cierre antes de tiempo: avisa a los socios a los que ese cierre les canceló clases. */
    public void closureLifted(Gym gym, GymClosure closure) {
        if (!closure.getEndDate().isBefore(LocalDate.now(GYM_ZONE))) {
            safely(gym, "cierre levantado", () -> {
                // Cierre de ultima hora: a todos los socios (se les aviso a todos al crearlo). Si no, solo a los afectados.
                List<Long> memberIds = closure.wasCreatedWithShortNotice()
                        ? appUserRepository.findByGymIdAndRole(gym.getId(), Role.MEMBER).stream()
                                .filter(AppUser::isActive)
                                .map(AppUser::getId)
                                .toList()
                        : reservationRepository.findByCancelledByClosureId(closure.getId()).stream()
                                .map(Reservation::getMemberId)
                                .distinct()
                                .toList();
                pushService.notifyUsers(memberIds, PushMessages.closureLifted(gym, closure.getId()));
            });
        }
    }

    /** El admin o un profesor canceló la reserva del socio. */
    public void reservationCancelledByStaff(Gym gym, GymBlock block, Reservation reservation) {
        safely(gym, "reserva cancelada por el equipo", () -> pushService.notifyUsers(
                List.of(reservation.getMemberId()),
                PushMessages.reservationCancelledByStaff(block, reservation.getClassDate(), reservation.getId())));
    }

    /** Cambió el dia o la hora de la clase: avisa a quienes tienen reserva vigente en ella. */
    public void blockRescheduled(Gym gym, GymBlock block, DayOfWeek oldDay, LocalTime oldStart) {
        safely(gym, "clase reprogramada", () -> {
            List<Long> memberIds = upcomingBooked(block).stream().map(Reservation::getMemberId).distinct().toList();
            pushService.notifyUsers(memberIds, PushMessages.blockRescheduled(block, oldDay, oldStart));
        });
    }

    /**
     * La clase se desactivó o se va a eliminar: avisa a quienes tienen reserva vigente en ella. Se llama ANTES de borrar
     * (el borrado arrastra las reservas), con la lista leida en ese momento.
     */
    public void blockCancelled(Gym gym, GymBlock block) {
        safely(gym, "clase cancelada", () -> {
            List<Reservation> booked = upcomingBooked(block);
            booked.stream().collect(java.util.stream.Collectors.groupingBy(Reservation::getMemberId)).forEach(
                    (memberId, reservations) -> pushService.notifyUsers(
                            List.of(memberId),
                            PushMessages.blockCancelled(block, reservations.stream().map(Reservation::getClassDate).toList())));
        });
    }

    /** El administrador le quitó el plan al socio. */
    public void planRemoved(Gym gym, Long memberId, int cancelledReservations) {
        safely(gym, "plan quitado", () ->
                pushService.notifyUsers(List.of(memberId), PushMessages.planRemoved(gym, cancelledReservations)));
    }

    private List<Reservation> upcomingBooked(GymBlock block) {
        return reservationRepository.findByGymBlockIdAndStatusAndClassDateGreaterThanEqual(
                block.getId(), ReservationStatus.BOOKED, LocalDate.now(GYM_ZONE));
    }

    private void safely(Gym gym, String what, Runnable action) {
        if (gym == null || !gym.isActive()) {
            return; // gimnasio en pausa: no se manda ningun aviso
        }
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("No se pudo avisar al socio ({}) en el gimnasio {}: {}", what, gym.getId(), e.getMessage());
        }
    }
}
