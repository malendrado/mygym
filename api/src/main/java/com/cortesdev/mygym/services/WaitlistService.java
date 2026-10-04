package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.ClassWaitlistEntry;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.ClassWaitlistRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.services.exception.BookingWindowClosedException;
import com.cortesdev.mygym.services.exception.GymBlockNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lista de espera por clase — pedido explícito del usuario ("pinponeado" en sesión, sin tocar
 * código hasta acordar el diseño): cuando un bloque está lleno, un socio pide que le avisen si
 * se libera un cupo, en vez de quedarse sin enterarse cuando alguien cancela.
 *
 * Orden de llegada (FIFO por created_at). Al primero de la lista se le avisa apenas se libera un
 * cupo, con {@link #HEAD_START} de ventaja antes de avisarle al resto — si nadie reserva en ese
 * tiempo, se le avisa a todos los demás de una (a partir de ahí es orden de llegada real: gana
 * el que reserve primero). Reservar (por cualquier vía) saca al socio de la lista de esa clase;
 * si no llega a tiempo, se queda anotado para la PRÓXIMA cancelación de esa misma clase.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class WaitlistService {

    private static final Logger log = LoggerFactory.getLogger(WaitlistService.class);
    private static final ZoneId GYM_ZONE = ZoneId.of("America/Santiago");
    private static final long HEAD_START_MINUTES = 10;

    private final ClassWaitlistRepository waitlistRepository;
    private final GymBlockRepository gymBlockRepository;
    private final ReservationRepository reservationRepository;
    private final AppUserRepository appUserRepository;
    private final GymRepository gymRepository;
    private final MemberLifecycleEmailService emailService;

    public void join(Long gymId, Long memberId, Long gymBlockId, LocalDate classDate) {
        GymBlock block = gymBlockRepository
                .findByIdAndGymId(gymBlockId, gymId)
                .filter(GymBlock::isActive)
                .orElseThrow(() -> new GymBlockNotFoundException(gymId, gymBlockId));

        int taken = reservationRepository.countByGymBlockIdAndClassDateAndStatus(
                gymBlockId, classDate, ReservationStatus.BOOKED);
        if (taken < block.getCapacity()) {
            throw new BookingWindowClosedException("Todavía queda cupo en esta clase — puedes reservar directo");
        }
        if (waitlistRepository
                .findByGymBlockIdAndClassDateAndMemberId(gymBlockId, classDate, memberId)
                .isPresent()) {
            throw new BookingWindowClosedException("Ya estás en la lista de espera de esta clase");
        }

        waitlistRepository.save(ClassWaitlistEntry.builder()
                .gymBlockId(gymBlockId)
                .memberId(memberId)
                .classDate(classDate)
                .build());
    }

    public void leave(Long memberId, Long gymBlockId, LocalDate classDate) {
        waitlistRepository
                .findByGymBlockIdAndClassDateAndMemberId(gymBlockId, classDate, memberId)
                .ifPresent(waitlistRepository::delete);
    }

    /** Reservó (por cualquier vía, no solo por venir de un aviso) — ya no tiene sentido que
     *  siga anotado a la espera de esa misma clase. */
    public void clearOnBooked(Long gymBlockId, LocalDate classDate, Long memberId) {
        waitlistRepository
                .findByGymBlockIdAndClassDateAndMemberId(gymBlockId, classDate, memberId)
                .ifPresent(waitlistRepository::delete);
    }

    /** Cierre de emergencia (ver GymClosureService) — a diferencia de onSpotFreed, acá NUNCA se
     *  avisa a nadie: la clase sigue cerrada, avisar "se liberó un cupo" sería falso. */
    public void clearForClosure(Long gymBlockId, LocalDate classDate) {
        waitlistRepository
                .findByGymBlockIdAndClassDateOrderByCreatedAtAsc(gymBlockId, classDate)
                .forEach(waitlistRepository::delete);
    }

    /** Se cancela una reserva → puede haber liberado 1 o más cupos (si varias se cancelan
     *  seguidas antes de que corra el escalamiento) — le da la ventana de ventaja a tantos de
     *  la cabeza de la lista como cupos reales queden libres ahora mismo. */
    public void onSpotFreed(Long gymBlockId, LocalDate classDate) {
        GymBlock block = gymBlockRepository.findById(gymBlockId).orElse(null);
        if (block == null) {
            return;
        }
        int taken = reservationRepository.countByGymBlockIdAndClassDateAndStatus(
                gymBlockId, classDate, ReservationStatus.BOOKED);
        int openSpots = block.getCapacity() - taken;
        if (openSpots <= 0) {
            return;
        }

        List<ClassWaitlistEntry> unnotified =
                waitlistRepository.findByGymBlockIdAndClassDateOrderByCreatedAtAsc(gymBlockId, classDate).stream()
                        .filter(e -> e.getNotifiedAt() == null)
                        .toList();
        if (unnotified.isEmpty()) {
            return;
        }
        Gym gym = gymRepository.findById(block.getGymId()).orElse(null);
        if (gym == null) {
            return;
        }

        for (int i = 0; i < Math.min(openSpots, unnotified.size()); i++) {
            notifyEntry(unnotified.get(i), gym, block, true);
        }
    }

    // Cada minuto: a cualquier ocurrencia cuya cabeza de lista ya haya agotado su ventana de
    // ventaja sin reservar, y todavía tenga cupo libre, se le avisa al resto de la lista de una.
    // Trae todo lo vigente en una sola query y agrupa en memoria — mismo criterio anti-N+1 que
    // usa ReservationService (pensado para el volumen real de un gym, no para miles de filas).
    @Scheduled(fixedDelay = 60_000)
    public void escalateExpiredHeadStarts() {
        LocalDate today = LocalDate.now(GYM_ZONE);
        List<ClassWaitlistEntry> upcoming = waitlistRepository.findByClassDateGreaterThanEqual(today);
        if (upcoming.isEmpty()) {
            return;
        }
        Instant cutoff = Instant.now().minus(HEAD_START_MINUTES, ChronoUnit.MINUTES);

        Map<String, List<ClassWaitlistEntry>> byOccurrence =
                upcoming.stream().collect(Collectors.groupingBy(e -> e.getGymBlockId() + "|" + e.getClassDate()));

        int escalated = 0;
        for (List<ClassWaitlistEntry> group : byOccurrence.values()) {
            boolean headStartExpired =
                    group.stream().anyMatch(e -> e.getNotifiedAt() != null && e.getNotifiedAt().isBefore(cutoff));
            List<ClassWaitlistEntry> unnotified =
                    group.stream().filter(e -> e.getNotifiedAt() == null).toList();
            if (!headStartExpired || unnotified.isEmpty()) {
                continue;
            }

            Long gymBlockId = group.get(0).getGymBlockId();
            LocalDate classDate = group.get(0).getClassDate();
            GymBlock block = gymBlockRepository.findById(gymBlockId).orElse(null);
            if (block == null) {
                continue;
            }
            int taken = reservationRepository.countByGymBlockIdAndClassDateAndStatus(
                    gymBlockId, classDate, ReservationStatus.BOOKED);
            if (taken >= block.getCapacity()) {
                continue; // el cupo ya no está — nadie más a quien avisarle por esta vuelta.
            }
            Gym gym = gymRepository.findById(block.getGymId()).orElse(null);
            if (gym == null) {
                continue;
            }
            for (ClassWaitlistEntry entry : unnotified) {
                notifyEntry(entry, gym, block, false);
                escalated++;
            }
        }
        if (escalated > 0) {
            log.info("WaitlistService: {} avisos de 'cupo abierto a todos' enviados", escalated);
        }
    }

    private void notifyEntry(ClassWaitlistEntry entry, Gym gym, GymBlock block, boolean headStart) {
        AppUser member = appUserRepository.findById(entry.getMemberId()).orElse(null);
        if (member == null) {
            waitlistRepository.delete(entry);
            return;
        }
        entry.setNotifiedAt(Instant.now());
        waitlistRepository.save(entry);
        if (headStart) {
            emailService.sendWaitlistHeadStart(gym, member, block, entry.getClassDate(), HEAD_START_MINUTES);
        } else {
            emailService.sendWaitlistSpotOpen(gym, member, block, entry.getClassDate());
        }
    }

    /** Para listOccurrences: en qué ocurrencias (gymBlockId+classDate) está anotado este socio,
     *  dentro del rango pedido — mismo patrón de 1 query + mapa en memoria que ya usa
     *  ReservationService para myReservationIdByOccurrence. */
    @Transactional(readOnly = true)
    public Set<String> myWaitlistedOccurrences(Long memberId, LocalDate from, LocalDate to) {
        return waitlistRepository.findByMemberIdAndClassDateBetween(memberId, from, to).stream()
                .map(e -> e.getGymBlockId() + "|" + e.getClassDate())
                .collect(Collectors.toSet());
    }
}
