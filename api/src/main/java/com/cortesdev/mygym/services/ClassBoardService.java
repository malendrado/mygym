package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.MemberWorkoutLog;
import com.cortesdev.mygym.models.PlanDay;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.WorkoutPlan;
import com.cortesdev.mygym.models.dto.ClassBoardResponse;
import com.cortesdev.mygym.models.dto.ClassBoardResponse.BoardAttendee;
import com.cortesdev.mygym.models.dto.ClassBoardResponse.BoardClass;
import com.cortesdev.mygym.models.dto.ClassBoardResponse.LastLog;
import com.cortesdev.mygym.models.dto.ClassBoardResponse.NextDay;
import com.cortesdev.mygym.models.dto.MemberWorkoutLogResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.MemberWorkoutLogRepository;
import com.cortesdev.mygym.repositories.PlanDayRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.repositories.WorkoutPlanRepository;
import com.cortesdev.mygym.services.exception.GymNotFoundException;
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

/**
 * Tablero "En clase" (pedido del usuario 2026-10-12, docs/plans/2026-10-12-en-clase-memoria-viva.md): las clases en
 * curso ahora (todas) y la siguiente, con los alumnos de cada una y su Memoria Viva — qué le toca hoy y qué hizo la
 * última vez. Una clase tiene como mucho su cupo de alumnos, así que todo se resuelve con un número FIJO de consultas
 * por llamada (en lote), sin una consulta por alumno: reservas, socios, planes, días, registros del ciclo, asistencias
 * recientes, registros de esas asistencias, títulos de días y "primera clase".
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassBoardService {

    private static final ZoneId GYM_ZONE = ZoneId.of("America/Santiago");

    // Hasta cuántos días atrás se busca "lo último que hizo": un socio que no viene hace más de esto aparece sin
    // registros recientes (en el detalle sigue viendo sus últimos 30 registros).
    static final int RECENT_DAYS = 60;
    // La siguiente clase se busca hasta una semana hacia adelante (como la TV).
    private static final int LOOKAHEAD_DAYS = 7;
    static final int MAX_EXERCISES_IN_CARD = 3;

    private final GymRepository gymRepository;
    private final GymBlockRepository gymBlockRepository;
    private final ReservationRepository reservationRepository;
    private final AppUserRepository appUserRepository;
    private final WorkoutPlanRepository workoutPlanRepository;
    private final PlanDayRepository planDayRepository;
    private final MemberWorkoutLogRepository memberWorkoutLogRepository;
    private final GymClosureService gymClosureService;
    private final WorkoutService workoutService;

    private record Slot(GymBlock block, LocalDate date, String status) {}

    public ClassBoardResponse board(Long gymId) {
        if (!gymRepository.existsById(gymId)) {
            throw new GymNotFoundException(gymId);
        }
        ZonedDateTime now = ZonedDateTime.now(GYM_ZONE);
        LocalDate today = now.toLocalDate();

        List<GymBlock> active =
                gymBlockRepository.findByGymId(gymId).stream().filter(GymBlock::isActive).toList();
        // Un cierre de emergencia saca la clase del tablero (igual que de la TV): no hay a quién esperar.
        GymClosureService.ClosureSchedule closures =
                gymClosureService.scheduleFor(gymId, today, today.plusDays(LOOKAHEAD_DAYS));

        List<Slot> slots = new ArrayList<>();
        active.stream()
                .filter(b -> b.getDayOfWeek() == today.getDayOfWeek())
                .filter(b -> !now.toLocalTime().isBefore(b.getStartTime()) && now.toLocalTime().isBefore(b.getEndTime()))
                .filter(b -> !closures.isClosed(b.getId(), today))
                .sorted(Comparator.comparing(GymBlock::getStartTime))
                .forEach(b -> slots.add(new Slot(b, today, "LIVE")));
        slots.addAll(nextSlots(active, now, closures));

        if (slots.isEmpty()) {
            return new ClassBoardResponse(Instant.now(), List.of());
        }
        return new ClassBoardResponse(Instant.now(), buildClasses(slots));
    }

    /** Las clases que empiezan primero después de ahora (hoy, o el próximo día con clases): puede ser más de una si
     *  son simultáneas. */
    private List<Slot> nextSlots(List<GymBlock> active, ZonedDateTime now, GymClosureService.ClosureSchedule closures) {
        for (int offset = 0; offset <= LOOKAHEAD_DAYS; offset++) {
            LocalDate date = now.toLocalDate().plusDays(offset);
            boolean isToday = offset == 0;
            List<GymBlock> day = active.stream()
                    .filter(b -> b.getDayOfWeek() == date.getDayOfWeek())
                    .filter(b -> !isToday || b.getStartTime().isAfter(now.toLocalTime()))
                    .filter(b -> !closures.isClosed(b.getId(), date))
                    .sorted(Comparator.comparing(GymBlock::getStartTime))
                    .toList();
            if (!day.isEmpty()) {
                var start = day.get(0).getStartTime();
                return day.stream()
                        .filter(b -> b.getStartTime().equals(start))
                        .map(b -> new Slot(b, date, "NEXT"))
                        .toList();
            }
        }
        return List.of();
    }

    private List<BoardClass> buildClasses(List<Slot> slots) {
        List<Long> blockIds = slots.stream().map(s -> s.block().getId()).distinct().toList();
        LocalDate minDate = slots.stream().map(Slot::date).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate maxDate = slots.stream().map(Slot::date).max(Comparator.naturalOrder()).orElseThrow();
        Set<String> slotKeys = slots.stream().map(s -> key(s.block().getId(), s.date())).collect(Collectors.toSet());

        // 1) reservas de las clases del tablero
        List<Reservation> reservations = reservationRepository
                .findByGymBlockIdInAndClassDateBetweenAndStatus(blockIds, minDate, maxDate, ReservationStatus.BOOKED)
                .stream()
                .filter(r -> slotKeys.contains(key(r.getGymBlockId(), r.getClassDate())))
                .toList();
        Set<Long> memberIds = reservations.stream().map(Reservation::getMemberId).collect(Collectors.toSet());

        Map<Long, BoardMemory> memories = memberIds.isEmpty() ? Map.of() : memoryFor(memberIds, reservations, minDate);

        // 2) socios (un solo findAllById)
        Map<Long, AppUser> members = memberIds.isEmpty()
                ? Map.of()
                : appUserRepository.findAllById(memberIds).stream()
                        .filter(u -> u.getRole() == Role.MEMBER)
                        .collect(Collectors.toMap(AppUser::getId, u -> u));

        List<BoardClass> out = new ArrayList<>();
        for (Slot slot : slots) {
            GymBlock b = slot.block();
            List<BoardAttendee> attendees = reservations.stream()
                    .filter(r -> r.getGymBlockId().equals(b.getId()) && r.getClassDate().equals(slot.date()))
                    .filter(r -> members.containsKey(r.getMemberId()))
                    .map(r -> toAttendee(r, members.get(r.getMemberId()), memories.get(r.getMemberId())))
                    .sorted(Comparator.comparing(a -> a.name() == null ? "" : a.name().toLowerCase()))
                    .toList();
            out.add(new BoardClass(
                    b.getId(),
                    b.getLabel(),
                    b.getCategory(),
                    b.getInstructorName(),
                    slot.date(),
                    b.getStartTime(),
                    b.getEndTime(),
                    slot.status(),
                    b.getCapacity(),
                    attendees));
        }
        return out;
    }

    private static String key(Long blockId, LocalDate date) {
        return blockId + "|" + date;
    }

    /** Todo lo que se necesita saber de un alumno para su tarjeta, ya calculado. */
    private record BoardMemory(
            String planName,
            NextDay nextDay,
            boolean hasPlan,
            // por reserva del tablero: registro de ESA clase, si existe
            Map<Long, MemberWorkoutLogResponse> thisClassLogByReservation,
            // por fecha de clase del tablero: lo último que hizo antes de esa clase
            Map<LocalDate, LastLog> lastLogByDate,
            Map<LocalDate, Boolean> unloggedLastByDate,
            boolean hasPriorBooking) {}

    private Map<Long, BoardMemory> memoryFor(Set<Long> memberIds, List<Reservation> boardReservations, LocalDate minDate) {
        // --- Plan activo y sus días: 2 consultas ---
        Map<Long, WorkoutPlan> planByMember = workoutPlanRepository.findByMemberIdInAndActiveTrue(memberIds).stream()
                .collect(Collectors.toMap(WorkoutPlan::getMemberId, p -> p, (a, b) -> a));
        Map<Long, List<PlanDay>> daysByPlan = planByMember.isEmpty()
                ? Map.of()
                : planDayRepository
                        .findByWorkoutPlanIdInOrderByOrderIndexAsc(planByMember.values().stream()
                                .map(WorkoutPlan::getId)
                                .toList())
                        .stream()
                        .collect(Collectors.groupingBy(PlanDay::getWorkoutPlanId));

        // --- "Qué le toca": el día del último registro de cada plan (por fecha de la clase): 1 consulta, sin los ejercicios ---
        Map<Long, Long> latestPlanDayByPlan = new HashMap<>();
        if (!daysByPlan.isEmpty()) {
            for (MemberWorkoutLogRepository.LatestPlanLog row :
                    memberWorkoutLogRepository.findLatestPlanDayByPlan(List.copyOf(daysByPlan.keySet()))) {
                latestPlanDayByPlan.put(row.getPlanId(), row.getPlanDayId());
            }
        }

        // --- "Lo último que hizo": sus asistencias recientes y los registros de esas clases: 2 consultas ---
        List<Reservation> attended = reservationRepository.findByMemberIdInAndCheckedInAtIsNotNullAndClassDateGreaterThanEqual(
                memberIds, minDate.minusDays(RECENT_DAYS));
        Set<Long> reservationIdsForLogs = new HashSet<>();
        attended.forEach(r -> reservationIdsForLogs.add(r.getId()));
        boardReservations.forEach(r -> reservationIdsForLogs.add(r.getId()));
        List<MemberWorkoutLog> logs = reservationIdsForLogs.isEmpty()
                ? List.of()
                : memberWorkoutLogRepository.findByReservationIdIn(List.copyOf(reservationIdsForLogs));
        Map<Long, MemberWorkoutLog> logByReservation = new HashMap<>();
        logs.forEach(l -> logByReservation.put(l.getReservationId(), l));

        // --- títulos de los días y nombres de quienes anotaron: 2 consultas ---
        Set<Long> dayIds = new HashSet<>();
        logs.forEach(l -> {
            dayIds.add(l.getSuggestedPlanDayId());
            if (l.getPlanDayId() != null) {
                dayIds.add(l.getPlanDayId());
            }
        });
        List<PlanDay> logDays = dayIds.isEmpty() ? List.of() : planDayRepository.findAllById(dayIds);
        Map<Long, String> staffNames = workoutService.staffNamesOf(logs);

        // --- "primera clase": 1 consulta ---
        LocalDate earliest = boardReservations.stream().map(Reservation::getClassDate).min(Comparator.naturalOrder()).orElse(minDate);
        Set<Long> withPrior = new HashSet<>(reservationRepository.findMemberIdsWithBookingBefore(memberIds, earliest));

        Map<Long, List<Reservation>> attendedByMember = attended.stream().collect(Collectors.groupingBy(Reservation::getMemberId));
        Map<Long, List<Reservation>> boardByMember = boardReservations.stream().collect(Collectors.groupingBy(Reservation::getMemberId));
        Map<Long, String> titleById = logDays.stream().collect(Collectors.toMap(PlanDay::getId, PlanDay::getTitle, (a, b) -> a));

        Map<Long, BoardMemory> result = new HashMap<>();
        for (Long memberId : memberIds) {
            WorkoutPlan plan = planByMember.get(memberId);
            List<PlanDay> days = plan == null ? List.of() : daysByPlan.getOrDefault(plan.getId(), List.of());
            boolean hasPlan = plan != null && !days.isEmpty();
            NextDay nextDay = null;
            if (hasPlan) {
                PlanDay next = WorkoutService.nextDayAfter(days, latestPlanDayByPlan.get(plan.getId()));
                nextDay = new NextDay(next.getId(), next.getTitle(), next.getOrderIndex(), days.size());
            }

            Map<Long, MemberWorkoutLogResponse> thisClass = new HashMap<>();
            Map<LocalDate, LastLog> lastByDate = new HashMap<>();
            Map<LocalDate, Boolean> unloggedByDate = new HashMap<>();
            for (Reservation board : boardByMember.getOrDefault(memberId, List.of())) {
                MemberWorkoutLog own = logByReservation.get(board.getId());
                if (own != null) {
                    thisClass.put(board.getId(), workoutService.toLogResponse(own, logDays, board.getClassDate(), staffNames));
                }
                // Sus asistencias anteriores a esta clase (sin contar la propia), la más reciente primero.
                List<Reservation> before = attendedByMember.getOrDefault(memberId, List.of()).stream()
                        .filter(r -> !r.getId().equals(board.getId()) && !r.getClassDate().isAfter(board.getClassDate()))
                        .sorted(Comparator.comparing(Reservation::getClassDate).reversed())
                        .toList();
                unloggedByDate.put(board.getClassDate(), !before.isEmpty() && !logByReservation.containsKey(before.get(0).getId()));
                before.stream()
                        .filter(r -> logByReservation.containsKey(r.getId()))
                        .findFirst()
                        .ifPresent(r -> lastByDate.put(board.getClassDate(), toLastLog(r, logByReservation.get(r.getId()), titleById)));
            }
            result.put(memberId, new BoardMemory(
                    plan == null ? null : plan.getName(), nextDay, hasPlan, thisClass, lastByDate, unloggedByDate, withPrior.contains(memberId)));
        }
        return result;
    }

    private static LastLog toLastLog(Reservation r, MemberWorkoutLog log, Map<Long, String> titleById) {
        boolean deviated = log.getPlanDayId() == null || !log.getPlanDayId().equals(log.getSuggestedPlanDayId());
        String done = log.getPlanDayId() != null ? titleById.get(log.getPlanDayId()) : log.getFreeTextLabel();
        List<com.cortesdev.mygym.models.ExerciseLogEntry> exercises = log.getExercisesLog() == null
                ? List.of()
                : log.getExercisesLog().stream().limit(MAX_EXERCISES_IN_CARD).toList();
        return new LastLog(r.getClassDate(), titleById.get(log.getSuggestedPlanDayId()), deviated, done, exercises);
    }

    private BoardAttendee toAttendee(Reservation r, AppUser member, BoardMemory memory) {
        boolean has = memory != null && memory.hasPlan();
        LocalDate date = r.getClassDate();
        return new BoardAttendee(
                member.getId(),
                member.getName(),
                member.getPhotoUrl(),
                member.getPhone(),
                r.getId(),
                r.getCheckedInAt(),
                r.getCheckinMethod(),
                has,
                memory == null ? null : memory.planName(),
                memory == null ? null : memory.nextDay(),
                memory == null ? null : memory.lastLogByDate().get(date),
                memory == null ? null : memory.thisClassLogByReservation().get(r.getId()),
                memory != null && Boolean.TRUE.equals(memory.unloggedLastByDate().get(date)),
                memory == null || !memory.hasPriorBooking());
    }
}
