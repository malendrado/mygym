package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.PushSubscription;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.PushSubscriptionRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recordatorio push "tu clase empieza en 1 hora" (pedido del usuario 2026-10-13). Una sola vez por reserva:
 * <ul>
 *   <li>Reservada con 60 min o mas de anticipacion: se avisa 60 min antes.</li>
 *   <li>Reservada con menos de 60 y al menos 30 min: se avisa 30 min antes.</li>
 *   <li>Reservada con menos de 30 min: no se avisa (ya sabe que va).</li>
 * </ul>
 * Corre cada 5 minutos, asi que el aviso puede salir hasta 5 min despues del momento exacto. `reminder_pushed_at` marca
 * las reservas ya resueltas (avisadas o descartadas) para no volver a evaluarlas. Supone UNA instancia de la API (igual
 * que los demas crons): con mas de una haria falta un candado.
 */
@Component
@RequiredArgsConstructor
public class ClassReminderJob {

    private static final Logger log = LoggerFactory.getLogger(ClassReminderJob.class);
    private static final ZoneId GYM_ZONE = ZoneId.of("America/Santiago");
    private static final long FIRST_REMINDER_MINUTES = 60;
    private static final long SECOND_REMINDER_MINUTES = 30;
    /** Si el cron se atraso y faltan menos de esto, ya no tiene sentido avisar. */
    private static final long TOO_LATE_MINUTES = 10;

    private final ReservationRepository reservationRepository;
    private final GymBlockRepository gymBlockRepository;
    private final GymRepository gymRepository;
    private final PushSubscriptionRepository subscriptionRepository;
    private final PushNotificationService pushService;

    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    @Transactional
    public void sendReminders() {
        ZonedDateTime now = ZonedDateTime.now(GYM_ZONE);
        LocalDate today = now.toLocalDate();
        List<Reservation> pending =
                reservationRepository.findByClassDateAndStatusAndReminderPushedAtIsNull(today, ReservationStatus.BOOKED);
        if (pending.isEmpty()) {
            return;
        }
        Map<Long, GymBlock> blocks = gymBlockRepository
                .findAllById(pending.stream().map(Reservation::getGymBlockId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(GymBlock::getId, b -> b));
        Map<Long, Gym> gyms = gymRepository
                .findAllById(blocks.values().stream().map(GymBlock::getGymId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Gym::getId, g -> g));
        Set<Long> subscribedMembers = subscriptionRepository
                .findByUserIdIn(pending.stream().map(Reservation::getMemberId).distinct().toList())
                .stream()
                .map(PushSubscription::getUserId)
                .collect(Collectors.toSet());

        Instant nowInstant = Instant.now();
        int sent = 0;
        for (Reservation reservation : pending) {
            GymBlock block = blocks.get(reservation.getGymBlockId());
            Gym gym = block == null ? null : gyms.get(block.getGymId());
            if (block == null || gym == null || !gym.isActive()) {
                continue; // gimnasio en pausa: sin recordatorios
            }
            ZonedDateTime start = today.atTime(block.getStartTime()).atZone(GYM_ZONE);
            long minutesToStart = Duration.between(now, start).toMinutes();
            if (minutesToStart > FIRST_REMINDER_MINUTES) {
                continue; // todavia falta; se evalua en una vuelta posterior
            }
            if (!subscribedMembers.contains(reservation.getMemberId())) {
                // Sin telefono suscrito no hay a quien avisar. Si la clase ya empezo se cierra la evaluacion.
                if (minutesToStart < 0) {
                    reservation.setReminderPushedAt(nowInstant);
                }
                continue;
            }
            long reminderMinutes = reminderMinutesFor(reservation, start);
            if (reminderMinutes == 0 || reservation.getCheckedInAt() != null || minutesToStart < TOO_LATE_MINUTES) {
                reservation.setReminderPushedAt(nowInstant); // descartada: nunca se avisara
                continue;
            }
            // Todavia no es el momento de esta reserva (ej. reservada con 45 min: se avisa a los 30).
            if (minutesToStart > reminderMinutes) {
                continue;
            }
            reservation.setReminderPushedAt(nowInstant);
            pushService.notifyUsers(
                    List.of(reservation.getMemberId()),
                    PushMessages.classReminder(gym, block, today, (int) minutesToStart, reservation.getId()));
            sent++;
        }
        reservationRepository.saveAll(pending);
        if (sent > 0) {
            log.info("ClassReminderJob: {} recordatorios de clase pedidos", sent);
        }
    }

    /** 60, 30 o 0 (= no avisar) segun con cuanta anticipacion se hizo la reserva. */
    static long reminderMinutesFor(Reservation reservation, ZonedDateTime start) {
        if (reservation.getCreatedAt() == null) {
            return FIRST_REMINDER_MINUTES;
        }
        long leadMinutes = Duration.between(reservation.getCreatedAt(), start.toInstant()).toMinutes();
        if (leadMinutes >= FIRST_REMINDER_MINUTES) {
            return FIRST_REMINDER_MINUTES;
        }
        if (leadMinutes >= SECOND_REMINDER_MINUTES) {
            return SECOND_REMINDER_MINUTES;
        }
        return 0;
    }
}
