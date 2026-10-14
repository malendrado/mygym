package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.MemberFixedClass;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.FixedClassGenerationResult;
import com.cortesdev.mygym.models.dto.FixedClassResponse;
import com.cortesdev.mygym.models.dto.FixedClassSkipped;
import com.cortesdev.mygym.models.dto.FixedClassesSaveResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.MemberFixedClassRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.services.exception.GymBlockNotFoundException;
import com.cortesdev.mygym.services.exception.InvalidFixedClassException;
import com.cortesdev.mygym.services.exception.MemberNotFoundException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Clases fijas por socio (2026-10-12, ver docs/plans/2026-10-12-clases-fijas.md): el admin o el
 * profesor deja una clase semanal precargada (día + hora + nombre = un bloque) y este servicio le
 * reserva cada fecha del período de su plan, también al renovar. Es el ÚNICO lugar que crea
 * reservas fijas.
 *
 * <p>El descuento de cupo no se calcula acá: una reserva BOOKED dentro del período ya cuenta
 * contra el plan (MemberService.remainingClasses), así que crear la reserva basta.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class FixedClassService {

    private static final Logger log = LoggerFactory.getLogger(FixedClassService.class);
    private static final ZoneId GYM_ZONE = ZoneId.of("America/Santiago");
    private static final DateTimeFormatter EMAIL_DATE = DateTimeFormatter.ofPattern("d 'de' MMMM", new Locale("es", "CL"));

    private final MemberFixedClassRepository fixedClassRepository;
    private final GymBlockRepository gymBlockRepository;
    private final AppUserRepository appUserRepository;
    private final ReservationRepository reservationRepository;
    private final GymRepository gymRepository;
    private final ReservationService reservationService;
    private final MemberService memberService;
    private final GymClosureService gymClosureService;
    private final WaitlistService waitlistService;
    private final MemberLifecycleEmailService memberLifecycleEmailService;

    // ---------------------------------------------------------------------------------------
    // Lectura
    // ---------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<FixedClassResponse> list(Long gymId, Long memberId) {
        findMemberOrThrow(gymId, memberId);
        return toResponses(fixedClassRepository.findByMemberId(memberId));
    }

    // ---------------------------------------------------------------------------------------
    // Edición
    // ---------------------------------------------------------------------------------------

    /** Deja al socio exactamente con estas clases fijas (lista vacía = ninguna), reserva lo que
     *  corresponda y devuelve qué pasó. Una clase fija que ya tenía se conserva (mismo id): así no
     *  se pierde el recuerdo de las fechas que el socio canceló a propósito. */
    public FixedClassesSaveResponse replaceForMember(Long gymId, Long memberId, List<Long> blockIds, Long actorUserId) {
        AppUser member = findMemberOrThrow(gymId, memberId);
        Map<Long, GymBlock> wanted = loadAndValidateBlocks(gymId, blockIds);

        List<MemberFixedClass> existing = fixedClassRepository.findByMemberId(memberId);
        Set<Long> existingBlockIds = existing.stream().map(MemberFixedClass::getGymBlockId).collect(Collectors.toSet());
        List<MemberFixedClass> toRemove =
                existing.stream().filter(f -> !wanted.containsKey(f.getGymBlockId())).toList();

        int cancelled = cancelFutureReservationsOf(memberId, toRemove);
        fixedClassRepository.deleteAll(toRemove);
        for (Long blockId : wanted.keySet()) {
            if (!existingBlockIds.contains(blockId)) {
                fixedClassRepository.save(MemberFixedClass.builder()
                        .gymId(gymId)
                        .memberId(memberId)
                        .gymBlockId(blockId)
                        .createdByUserId(actorUserId)
                        .build());
            }
        }

        FixedClassGenerationResult generation = generateForCurrentPeriod(member);
        return new FixedClassesSaveResponse(
                toResponses(fixedClassRepository.findByMemberId(memberId)),
                generation.created(),
                generation.skipped(),
                cancelled,
                generation.noActivePlan(),
                generation.stoppedByQuota());
    }

    // ---------------------------------------------------------------------------------------
    // Generación de reservas (idempotente)
    // ---------------------------------------------------------------------------------------

    /** Renovación: cuando un pago (Flow o manual) deja un período nuevo, se reservan solas las
     *  clases fijas — DESPUÉS del commit del pago y en su propia transacción, así que un error
     *  acá nunca revierte el pago (solo queda en el log). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMembershipPaid(MembershipPaidEvent event) {
        try {
            appUserRepository
                    .findById(event.memberId())
                    // Gimnasio en pausa: las clases fijas no se renuevan solas (se reservan cuando lo reactiven y haya un pago).
                    .filter(m -> m.getGymId() != null && gymRepository.existsByIdAndActiveTrue(m.getGymId()))
                    .ifPresent(this::generateForCurrentPeriod);
        } catch (RuntimeException e) {
            log.warn("No se pudieron reservar las clases fijas del socio {}: {}", event.memberId(), e.getMessage());
        }
    }

    /**
     * Reserva las clases fijas del socio desde hoy hasta el fin de su período, en orden
     * cronológico, hasta completar lo que le queda del plan. Idempotente: se puede llamar tantas
     * veces como se quiera (editar, volver a subir el Excel, renovar) sin duplicar nada ni volver
     * a imponer una fecha que el socio canceló. NO valida capacidad (sobrecupo: la clase fija
     * tiene prioridad) ni la ventana de reserva (la carga la hace el equipo del gimnasio).
     */
    public FixedClassGenerationResult generateForCurrentPeriod(AppUser member) {
        List<MemberFixedClass> fixedClasses = fixedClassRepository.findByMemberId(member.getId());
        if (fixedClasses.isEmpty()) {
            return FixedClassGenerationResult.none(false);
        }
        if (!memberService.hasActiveMembership(member)) {
            // Las clases quedan guardadas; se reservan cuando pague (ver onMembershipPaid).
            return FixedClassGenerationResult.none(true);
        }
        MemberService.PeriodRange period = memberService.currentPeriod(member);
        LocalDate today = LocalDate.now(GYM_ZONE);
        LocalTime nowTime = LocalTime.now(GYM_ZONE);
        LocalDate from = period.start().isAfter(today) ? period.start() : today;
        LocalDate to = period.endInclusive();
        if (from.isAfter(to)) {
            return FixedClassGenerationResult.none(false);
        }

        Map<Long, GymBlock> blocksById = gymBlockRepository
                .findAllById(fixedClasses.stream().map(MemberFixedClass::getGymBlockId).toList())
                .stream()
                .collect(Collectors.toMap(GymBlock::getId, b -> b));

        record Occurrence(LocalDate date, GymBlock block, MemberFixedClass fixed) {}
        List<Occurrence> occurrences = new ArrayList<>();
        for (MemberFixedClass fixed : fixedClasses) {
            GymBlock block = blocksById.get(fixed.getGymBlockId());
            if (block == null || !block.isActive()) {
                continue; // bloque desactivado: la clase fija queda guardada pero no reserva
            }
            for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                if (date.getDayOfWeek() != block.getDayOfWeek()) {
                    continue;
                }
                if (date.equals(today) && !block.getStartTime().isAfter(nowTime)) {
                    continue; // la clase de hoy ya empezó
                }
                occurrences.add(new Occurrence(date, block, fixed));
            }
        }
        occurrences.sort(Comparator.comparing(Occurrence::date).thenComparing(o -> o.block().getStartTime()));

        // Reservas del socio en el rango, de cualquier estado: las BOOKED evitan duplicar; las
        // CANCELLED de una clase fija (canceladas por el socio o el admin, no por un cierre) son
        // fechas que se soltaron a propósito y no se vuelven a imponer.
        Set<String> booked = new HashSet<>();
        Set<String> released = new HashSet<>();
        for (Reservation r : reservationRepository.findByMemberIdAndClassDateBetween(member.getId(), from, to)) {
            if (r.getStatus() == ReservationStatus.BOOKED) {
                booked.add(r.getGymBlockId() + "|" + r.getClassDate());
            } else if (r.getFixedClassId() != null && r.getCancelledByClosureId() == null) {
                released.add(r.getFixedClassId() + "|" + r.getClassDate());
            }
        }

        // Cierres del período en 1-2 consultas (nunca una por fecha); el motivo se pide solo para las cerradas.
        GymClosureService.ClosureSchedule closures = gymClosureService.scheduleFor(member.getGymId(), from, to);
        Integer remaining = memberService.remainingClasses(member); // null = sin tope
        List<FixedClassSkipped> skipped = new ArrayList<>();
        boolean stoppedByQuota = false;
        int created = 0;
        LocalDate firstCreated = null;
        LocalDate lastCreated = null;
        for (Occurrence o : occurrences) {
            if (booked.contains(o.block().getId() + "|" + o.date()) || released.contains(o.fixed().getId() + "|" + o.date())) {
                continue;
            }
            if (closures.isClosed(o.block().getId(), o.date())) {
                String closedReason = closures.reasonFor(o.block().getId(), o.date());
                skipped.add(new FixedClassSkipped(
                        o.date(), o.block().getLabel(), "Cierre" + (closedReason != null ? ": " + closedReason : "")));
                continue;
            }
            if (remaining != null && remaining <= 0) {
                stoppedByQuota = true;
                break;
            }
            reservationService.createBooked(o.block().getId(), member.getId(), o.date(), o.fixed().getId());
            waitlistService.clearOnBooked(o.block().getId(), o.date(), member.getId());
            if (remaining != null) {
                remaining--;
            }
            created++;
            firstCreated = firstCreated == null ? o.date() : firstCreated;
            lastCreated = o.date();
        }

        if (created > 0) {
            sendScheduledEmail(member, blocksById.values().stream().filter(GymBlock::isActive).toList(), created, firstCreated, lastCreated, skipped);
        }
        return new FixedClassGenerationResult(created, skipped, false, stoppedByQuota);
    }

    // ---------------------------------------------------------------------------------------
    // Internos
    // ---------------------------------------------------------------------------------------

    /** Cancela las reservas futuras (que aún no empiezan) nacidas de las clases fijas que se quitan.
     *  fixedClassId queda en null: no son "fechas que el socio soltó". Las pasadas no se tocan. */
    private int cancelFutureReservationsOf(Long memberId, List<MemberFixedClass> removed) {
        if (removed.isEmpty()) {
            return 0;
        }
        Set<Long> removedIds = removed.stream().map(MemberFixedClass::getId).collect(Collectors.toSet());
        LocalDate today = LocalDate.now(GYM_ZONE);
        LocalTime nowTime = LocalTime.now(GYM_ZONE);
        List<Reservation> candidates = reservationRepository
                .findByMemberIdAndStatusAndClassDateGreaterThanEqual(memberId, ReservationStatus.BOOKED, today)
                .stream()
                .filter(r -> r.getFixedClassId() != null && removedIds.contains(r.getFixedClassId()))
                .toList();
        Map<Long, GymBlock> blocksById = gymBlockRepository
                .findAllById(candidates.stream().map(Reservation::getGymBlockId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(GymBlock::getId, b -> b));
        int cancelled = 0;
        for (Reservation r : candidates) {
            GymBlock block = blocksById.get(r.getGymBlockId());
            if (block != null && r.getClassDate().equals(today) && !block.getStartTime().isAfter(nowTime)) {
                continue; // ya empezó: queda como historial
            }
            r.setStatus(ReservationStatus.CANCELLED);
            r.setFixedClassId(null);
            reservationRepository.save(r);
            waitlistService.onSpotFreed(r.getGymBlockId(), r.getClassDate());
            cancelled++;
        }
        return cancelled;
    }

    private Map<Long, GymBlock> loadAndValidateBlocks(Long gymId, List<Long> blockIds) {
        Map<Long, GymBlock> blocks = new java.util.LinkedHashMap<>();
        for (Long blockId : new LinkedHashSet<>(blockIds)) {
            GymBlock block = gymBlockRepository
                    .findByIdAndGymId(blockId, gymId)
                    .orElseThrow(() -> new GymBlockNotFoundException(gymId, blockId));
            if (!block.isActive()) {
                throw new InvalidFixedClassException("La clase " + FixedClassFormat.describeShort(block) + " está desactivada.");
            }
            blocks.put(blockId, block);
        }
        Map<DayOfWeek, GymBlock> byDay = new EnumMap<>(DayOfWeek.class);
        for (GymBlock block : blocks.values()) {
            GymBlock previous = byDay.put(block.getDayOfWeek(), block);
            if (previous != null) {
                throw new InvalidFixedClassException("Solo puede tener una clase fija por día: el "
                        + FixedClassFormat.dayLong(block.getDayOfWeek()) + " eligió "
                        + FixedClassFormat.describeShort(previous) + " y " + FixedClassFormat.describeShort(block) + ".");
            }
        }
        return blocks;
    }

    private AppUser findMemberOrThrow(Long gymId, Long memberId) {
        return appUserRepository
                .findByIdAndGymId(memberId, gymId)
                .filter(u -> u.getRole() == Role.MEMBER)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
    }

    private List<FixedClassResponse> toResponses(List<MemberFixedClass> fixed) {
        if (fixed.isEmpty()) {
            return List.of();
        }
        Map<Long, GymBlock> blocksById = gymBlockRepository
                .findAllById(fixed.stream().map(MemberFixedClass::getGymBlockId).toList())
                .stream()
                .collect(Collectors.toMap(GymBlock::getId, b -> b));
        return fixed.stream()
                .map(f -> blocksById.get(f.getGymBlockId()))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(GymBlock::getDayOfWeek).thenComparing(GymBlock::getStartTime))
                .map(b -> new FixedClassResponse(
                        b.getId(),
                        b.getLabel(),
                        b.getDayOfWeek(),
                        b.getStartTime(),
                        b.getEndTime(),
                        b.getCategory(),
                        b.getInstructorName(),
                        b.isActive()))
                .toList();
    }

    // Best-effort: un fallo de email nunca tumba las reservas (send ya traga sus errores; esto cubre el resto).
    private void sendScheduledEmail(
            AppUser member, List<GymBlock> blocks, int created, LocalDate first, LocalDate last, List<FixedClassSkipped> skipped) {
        try {
            Gym gym = gymRepository.findById(member.getGymId()).orElse(null);
            if (gym == null) {
                return;
            }
            String classesText = blocks.stream()
                    .sorted(Comparator.comparing(GymBlock::getDayOfWeek).thenComparing(GymBlock::getStartTime))
                    .map(FixedClassFormat::describeLong)
                    .collect(Collectors.joining(", "));
            String skippedNote = null;
            if (!skipped.isEmpty()) {
                skippedNote = "No reservamos "
                        + skipped.stream().map(s -> EMAIL_DATE.format(s.date())).distinct().collect(Collectors.joining(", "))
                        + " porque el gimnasio estará cerrado.";
            }
            memberLifecycleEmailService.sendFixedClassesScheduled(
                    gym, member, classesText, created, EMAIL_DATE.format(first), EMAIL_DATE.format(last), skippedNote);
        } catch (RuntimeException e) {
            log.warn("No se pudo avisar las clases fijas al socio {}: {}", member.getId(), e.getMessage());
        }
    }
}
