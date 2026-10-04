package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.GymClosure;
import com.cortesdev.mygym.models.GymClosureBlock;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.GymClosureCreateRequest;
import com.cortesdev.mygym.models.dto.GymClosureNoticeResponse;
import com.cortesdev.mygym.models.dto.GymClosurePreviewResponse;
import com.cortesdev.mygym.models.dto.GymClosureResponse;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymClosureBlockRepository;
import com.cortesdev.mygym.repositories.GymClosureRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.services.exception.GymClosureNotFoundException;
import com.cortesdev.mygym.services.exception.GymNotFoundException;
import com.cortesdev.mygym.services.exception.InvalidClosureRequestException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Cierre de emergencia de gimnasio — el admin del gym o el super-admin cierra un rango de fechas
 * (completo o clases puntuales) por fuerza mayor/seguridad. Cancela reservas futuras afectadas
 * (nunca las ya empezadas o con check-in), bloquea reservas nuevas mientras dure, y dispara el
 * aviso por email DESPUÉS de confirmada la transacción (ver ClosureNotificationService) — mandar
 * un email por socio afectado dentro de la misma transacción tendría el mismo problema de pool
 * de 5 conexiones ya documentado para otros fan-outs de este proyecto (ver Historial/TV).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class GymClosureService {

    private static final ZoneId GYM_ZONE = ZoneId.of("America/Santiago");
    private static final int MAX_REASON_LENGTH = 500;

    private final GymClosureRepository gymClosureRepository;
    private final GymClosureBlockRepository gymClosureBlockRepository;
    private final GymBlockRepository gymBlockRepository;
    private final ReservationRepository reservationRepository;
    private final GymRepository gymRepository;
    private final WaitlistService waitlistService;
    private final ClosureNotificationService closureNotificationService;

    /** Qué ocurrencias (día completo, o bloque+día puntual) quedan cerradas dentro de un rango —
     *  calculado una sola vez por llamada a listOccurrences/getSchedule, nunca por ocurrencia. */
    public record ClosureSchedule(Set<LocalDate> closedDates, Set<String> closedBlockDateKeys) {
        public boolean isClosed(Long gymBlockId, LocalDate date) {
            return closedDates.contains(date) || closedBlockDateKeys.contains(gymBlockId + "|" + date);
        }
    }

    private record AffectedOccurrence(GymBlock block, LocalDate date) {}

    @Transactional(readOnly = true)
    public ClosureSchedule scheduleFor(Long gymId, LocalDate from, LocalDate to) {
        List<GymClosure> candidates =
                gymClosureRepository.findByGymIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(gymId, to, from);
        Set<LocalDate> closedDates = new HashSet<>();
        Set<String> closedBlockDateKeys = new HashSet<>();
        List<Long> specificIds = new ArrayList<>();
        Map<Long, GymClosure> byId = new HashMap<>();
        for (GymClosure closure : candidates) {
            LocalDate start = closure.getStartDate().isAfter(from) ? closure.getStartDate() : from;
            LocalDate end = closure.effectiveEndDate().isBefore(to) ? closure.effectiveEndDate() : to;
            if (start.isAfter(end)) {
                continue;
            }
            if (closure.isWholeDays()) {
                for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                    closedDates.add(d);
                }
            } else {
                specificIds.add(closure.getId());
                byId.put(closure.getId(), closure);
            }
        }
        if (!specificIds.isEmpty()) {
            for (GymClosureBlock gcb : gymClosureBlockRepository.findByClosureIdIn(specificIds)) {
                GymClosure closure = byId.get(gcb.getClosureId());
                LocalDate start = closure.getStartDate().isAfter(from) ? closure.getStartDate() : from;
                LocalDate end = closure.effectiveEndDate().isBefore(to) ? closure.effectiveEndDate() : to;
                for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                    closedBlockDateKeys.add(gcb.getGymBlockId() + "|" + d);
                }
            }
        }
        return new ClosureSchedule(closedDates, closedBlockDateKeys);
    }

    /** Conveniencia para un único gymBlockId+fecha (ReservationService.book) — devuelve el motivo
     *  del cierre que aplica, o null si no hay ninguno. */
    @Transactional(readOnly = true)
    public String closedReasonFor(Long gymId, Long gymBlockId, LocalDate date) {
        List<GymClosure> candidates =
                gymClosureRepository.findByGymIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(gymId, date, date);
        for (GymClosure closure : candidates) {
            if (!closure.isActiveOn(date)) {
                continue;
            }
            if (closure.isWholeDays() || gymClosureBlockRepository.existsByClosureIdAndGymBlockId(closure.getId(), gymBlockId)) {
                return closure.getReason();
            }
        }
        return null;
    }

    /** Para el banner de /member — el cierre vigente o próximo más cercano del gym, si hay alguno. */
    @Transactional(readOnly = true)
    public GymClosureNoticeResponse activeOrUpcomingNotice(Long gymId) {
        LocalDate today = LocalDate.now(GYM_ZONE);
        return gymClosureRepository.findByGymIdOrderByCreatedAtDesc(gymId).stream()
                .filter(c -> !c.effectiveEndDate().isBefore(today))
                .min(Comparator.comparing(GymClosure::getStartDate))
                .map(c -> new GymClosureNoticeResponse(c.getStartDate(), c.effectiveEndDate(), c.getReason(), c.isWholeDays()))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public GymClosurePreviewResponse preview(Long gymId, GymClosureCreateRequest request) {
        return summarize(resolveAffectedOccurrences(gymId, request));
    }

    public GymClosureResponse create(Long gymId, GymClosureCreateRequest request, String createdByEmail, Role createdByRole) {
        gymRepository.findById(gymId).orElseThrow(() -> new GymNotFoundException(gymId));
        List<AffectedOccurrence> occurrences = resolveAffectedOccurrences(gymId, request);

        GymClosure closure = gymClosureRepository.save(GymClosure.builder()
                .gymId(gymId)
                .startDate(request.startDate())
                .endDate(request.endDate())
                .wholeDays(request.wholeDays())
                .reason(request.reason().trim())
                .createdByEmail(createdByEmail)
                .createdByRole(createdByRole.name())
                .createdAt(Instant.now())
                .build());

        if (!request.wholeDays()) {
            for (Long blockId : distinctBlockIds(request)) {
                gymClosureBlockRepository.save(
                        GymClosureBlock.builder().closureId(closure.getId()).gymBlockId(blockId).build());
            }
        }

        Map<Long, List<Reservation>> cancelledByMember = new HashMap<>();
        for (AffectedOccurrence occurrence : occurrences) {
            List<Reservation> booked = reservationRepository.findByGymBlockIdAndClassDateAndStatus(
                    occurrence.block().getId(), occurrence.date(), ReservationStatus.BOOKED);
            for (Reservation reservation : booked) {
                if (isAlreadyStartedOrCheckedIn(reservation, occurrence.block())) {
                    continue;
                }
                reservation.setStatus(ReservationStatus.CANCELLED);
                reservation.setCancelledByClosureId(closure.getId());
                reservationRepository.save(reservation);
                cancelledByMember.computeIfAbsent(reservation.getMemberId(), k -> new ArrayList<>()).add(reservation);
            }
            // A propósito NUNCA WaitlistService.onSpotFreed — eso mandaría "se liberó un cupo"
            // para una clase que en realidad sigue cerrada. Solo se limpia la lista en silencio.
            waitlistService.clearForClosure(occurrence.block().getId(), occurrence.date());
        }

        closure.setCancelledReservationsCount(
                cancelledByMember.values().stream().mapToInt(List::size).sum());
        closure.setAffectedMembersCount(cancelledByMember.size());
        gymClosureRepository.save(closure);

        Long closureId = closure.getId();
        registerAfterCommit(() -> closureNotificationService.notify(closureId, createdByRole));

        return toResponse(closure);
    }

    public void lift(Long gymId, Long closureId) {
        GymClosure closure = gymClosureRepository
                .findByIdAndGymId(closureId, gymId)
                .orElseThrow(() -> new GymClosureNotFoundException(closureId));
        if (closure.getLiftedAt() == null) {
            closure.setLiftedAt(Instant.now());
            gymClosureRepository.save(closure);
        }
    }

    @Transactional(readOnly = true)
    public List<GymClosureResponse> list(Long gymId) {
        return gymClosureRepository.findByGymIdOrderByCreatedAtDesc(gymId).stream()
                .map(this::toResponse)
                .toList();
    }

    private void registerAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private List<AffectedOccurrence> resolveAffectedOccurrences(Long gymId, GymClosureCreateRequest request) {
        validate(request);
        List<GymBlock> blocks =
                gymBlockRepository.findByGymId(gymId).stream().filter(GymBlock::isActive).toList();
        Set<Long> blockIds = request.wholeDays() ? null : distinctBlockIds(request);
        if (blockIds != null) {
            Set<Long> gymBlockIds = blocks.stream().map(GymBlock::getId).collect(Collectors.toSet());
            for (Long id : blockIds) {
                if (!gymBlockIds.contains(id)) {
                    throw new InvalidClosureRequestException("Uno de los bloques elegidos no pertenece a este gimnasio");
                }
            }
        }

        List<AffectedOccurrence> occurrences = new ArrayList<>();
        for (GymBlock block : blocks) {
            if (blockIds != null && !blockIds.contains(block.getId())) {
                continue;
            }
            for (LocalDate date = request.startDate(); !date.isAfter(request.endDate()); date = date.plusDays(1)) {
                if (date.getDayOfWeek() != block.getDayOfWeek()) {
                    continue;
                }
                occurrences.add(new AffectedOccurrence(block, date));
            }
        }
        return occurrences;
    }

    private GymClosurePreviewResponse summarize(List<AffectedOccurrence> occurrences) {
        Set<Long> members = new HashSet<>();
        int total = 0;
        for (AffectedOccurrence occurrence : occurrences) {
            List<Reservation> booked = reservationRepository.findByGymBlockIdAndClassDateAndStatus(
                    occurrence.block().getId(), occurrence.date(), ReservationStatus.BOOKED);
            for (Reservation reservation : booked) {
                if (isAlreadyStartedOrCheckedIn(reservation, occurrence.block())) {
                    continue;
                }
                total++;
                members.add(reservation.getMemberId());
            }
        }
        return new GymClosurePreviewResponse(total, members.size());
    }

    private boolean isAlreadyStartedOrCheckedIn(Reservation reservation, GymBlock block) {
        if (reservation.getCheckedInAt() != null) {
            return true;
        }
        ZonedDateTime classStart = ZonedDateTime.of(reservation.getClassDate(), block.getStartTime(), GYM_ZONE);
        return !ZonedDateTime.now(GYM_ZONE).isBefore(classStart);
    }

    private void validate(GymClosureCreateRequest request) {
        if (request.startDate().isAfter(request.endDate())) {
            throw new InvalidClosureRequestException("La fecha de inicio no puede ser posterior a la de término");
        }
        if (request.reason() == null || request.reason().trim().isEmpty()) {
            throw new InvalidClosureRequestException("Tienes que explicar el motivo del cierre");
        }
        if (request.reason().trim().length() > MAX_REASON_LENGTH) {
            throw new InvalidClosureRequestException(
                    "El motivo es demasiado largo (máximo " + MAX_REASON_LENGTH + " caracteres)");
        }
        if (!request.wholeDays() && (request.blockIds() == null || request.blockIds().isEmpty())) {
            throw new InvalidClosureRequestException("Elige al menos una clase para cerrar");
        }
    }

    private Set<Long> distinctBlockIds(GymClosureCreateRequest request) {
        return request.blockIds() == null ? Set.of() : new HashSet<>(request.blockIds());
    }

    private GymClosureResponse toResponse(GymClosure closure) {
        List<Long> blockIds = closure.isWholeDays()
                ? List.of()
                : gymClosureBlockRepository.findByClosureId(closure.getId()).stream()
                        .map(GymClosureBlock::getGymBlockId)
                        .toList();
        LocalDate today = LocalDate.now(GYM_ZONE);
        boolean active = !today.isBefore(closure.getStartDate()) && !today.isAfter(closure.effectiveEndDate());
        return new GymClosureResponse(
                closure.getId(),
                closure.getStartDate(),
                closure.getEndDate(),
                closure.isWholeDays(),
                blockIds,
                closure.getReason(),
                closure.getCreatedByEmail(),
                closure.getCreatedByRole(),
                closure.getCreatedAt(),
                closure.getLiftedAt(),
                active,
                closure.getCancelledReservationsCount(),
                closure.getAffectedMembersCount(),
                closure.getEmailsSent(),
                closure.getEmailsFailed());
    }
}
