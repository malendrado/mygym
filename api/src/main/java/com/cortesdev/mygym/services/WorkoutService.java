package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.MemberWorkoutLog;
import com.cortesdev.mygym.models.PlanDay;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.WorkoutPlan;
import com.cortesdev.mygym.models.dto.MemberWorkoutLogResponse;
import com.cortesdev.mygym.models.dto.PendingWorkoutResponse;
import com.cortesdev.mygym.models.dto.PlanDayResponse;
import com.cortesdev.mygym.models.dto.WorkoutLogSaveRequest;
import com.cortesdev.mygym.models.dto.WorkoutPlanCreateRequest;
import com.cortesdev.mygym.models.dto.WorkoutPlanResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.MemberWorkoutLogRepository;
import com.cortesdev.mygym.repositories.PlanDayRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.repositories.WorkoutPlanRepository;
import com.cortesdev.mygym.services.exception.InvalidWorkoutLogException;
import com.cortesdev.mygym.services.exception.MemberNotFoundException;
import com.cortesdev.mygym.services.exception.ReservationNotFoundException;
import com.cortesdev.mygym.services.exception.WorkoutLogNotFoundException;
import com.cortesdev.mygym.services.exception.WorkoutPlanNotFoundException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Memoria Viva" — bitácora cíclica de rutinas. Ver diseño completo + gaps cerrados en la
 * memoria de proyecto mygym_memoria_viva_design (sesión 2026-09-30 / 2026-10-02): el profesor
 * (o GYM_ADMIN) define una pauta de N días simples, el socio anota qué hizo en cada reserva con
 * check-in real. Sin restricción de "mis alumnos" — cualquier profesor/admin del gym opera sobre
 * cualquier socio, a propósito.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class WorkoutService {

    private final WorkoutPlanRepository workoutPlanRepository;
    private final PlanDayRepository planDayRepository;
    private final MemberWorkoutLogRepository memberWorkoutLogRepository;
    private final ReservationRepository reservationRepository;
    private final AppUserRepository appUserRepository;

    // ---- Lado profesor / gym-admin ----

    @Transactional(readOnly = true)
    public WorkoutPlanResponse getActivePlan(Long gymId, Long memberId) {
        requireMember(gymId, memberId);
        return workoutPlanRepository.findByMemberIdAndActiveTrue(memberId).map(this::toResponse).orElse(null);
    }

    /** Redefinir la rutina = desactivar el plan activo anterior (si había) y crear uno nuevo con
     *  estos días — nunca se edita un plan in-place, así los logs viejos quedan intactos
     *  apuntando a un plan íntegro, como historia (decisión explícita del usuario). */
    public WorkoutPlanResponse replacePlan(Long gymId, Long memberId, WorkoutPlanCreateRequest request) {
        requireMember(gymId, memberId);
        workoutPlanRepository.findByMemberIdAndActiveTrue(memberId).ifPresent(old -> {
            old.setActive(false);
            workoutPlanRepository.save(old);
        });

        WorkoutPlan plan = workoutPlanRepository.save(
                WorkoutPlan.builder().memberId(memberId).name(request.name()).active(true).build());

        List<PlanDay> days = new ArrayList<>();
        int order = 1;
        for (String title : request.dayTitles()) {
            days.add(planDayRepository.save(
                    PlanDay.builder().workoutPlanId(plan.getId()).orderIndex(order++).title(title).build()));
        }
        return toResponse(plan, days);
    }

    /** La "memoria" que ve el profesor antes de hablar con el socio — el último registro, sin
     *  importar si viene del plan activo o de uno ya reemplazado (el socio puede no haber
     *  registrado nada todavía bajo el plan nuevo). */
    @Transactional(readOnly = true)
    public MemberWorkoutLogResponse getLatestLog(Long gymId, Long memberId) {
        requireMember(gymId, memberId);
        WorkoutPlan plan = workoutPlanRepository.findByMemberIdAndActiveTrue(memberId).orElse(null);
        if (plan == null) {
            return null;
        }
        List<PlanDay> days = planDayRepository.findByWorkoutPlanIdOrderByOrderIndexAsc(plan.getId());
        MemberWorkoutLog latest = latestLog(memberWorkoutLogRepository.findBySuggestedPlanDayIdIn(planDayIds(days)));
        return latest == null ? null : toLogResponse(latest, days);
    }

    // ---- Lado socio (self-service, /api/me) ----

    @Transactional(readOnly = true)
    public PendingWorkoutResponse getPendingWorkout(Long memberId) {
        WorkoutPlan plan = workoutPlanRepository.findByMemberIdAndActiveTrue(memberId).orElse(null);
        if (plan == null) {
            return new PendingWorkoutResponse("NO_PLAN", null, null, null, null);
        }
        List<PlanDay> days = planDayRepository.findByWorkoutPlanIdOrderByOrderIndexAsc(plan.getId());
        Reservation pending = findPendingReservation(memberId);
        if (pending == null) {
            return new PendingWorkoutResponse("NO_PENDING", null, null, toResponse(plan, days), null);
        }
        PlanDay suggested = getNextPlanDay(days);
        return new PendingWorkoutResponse(
                "READY", pending.getId(), pending.getClassDate(), toResponse(plan, days), suggested.getId());
    }

    public MemberWorkoutLogResponse createLog(Long memberId, Long reservationId, WorkoutLogSaveRequest request) {
        Reservation reservation = reservationRepository
                .findByIdAndMemberId(reservationId, memberId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        if (reservation.getCheckedInAt() == null) {
            throw new InvalidWorkoutLogException("Esta clase todavía no tiene asistencia marcada.");
        }
        if (memberWorkoutLogRepository.findByReservationId(reservationId).isPresent()) {
            throw new InvalidWorkoutLogException("Esta clase ya tiene un registro.");
        }
        WorkoutPlan plan = workoutPlanRepository
                .findByMemberIdAndActiveTrue(memberId)
                .orElseThrow(() -> new WorkoutPlanNotFoundException(memberId));
        List<PlanDay> days = planDayRepository.findByWorkoutPlanIdOrderByOrderIndexAsc(plan.getId());
        PlanDay suggested = getNextPlanDay(days);
        PlanDay confirmed = resolveConfirmedDay(request, days);

        MemberWorkoutLog log = memberWorkoutLogRepository.save(MemberWorkoutLog.builder()
                .reservationId(reservationId)
                .suggestedPlanDayId(suggested.getId())
                .planDayId(confirmed != null ? confirmed.getId() : null)
                .freeTextLabel(confirmed == null ? request.freeTextLabel() : null)
                .exercisesLog(request.exercises())
                .build());
        return toLogResponse(log, days, reservation.getClassDate());
    }

    /** El log sí es editable después de creado (decisión explícita del usuario) — sin ventana de
     *  tiempo. Valida el día elegido contra los días del plan AL QUE PERTENECE ESE LOG (el de
     *  suggestedPlanDayId), no contra el plan activo actual: puede ya no ser el mismo. */
    public MemberWorkoutLogResponse updateLog(Long memberId, Long logId, WorkoutLogSaveRequest request) {
        MemberWorkoutLog log =
                memberWorkoutLogRepository.findById(logId).orElseThrow(() -> new WorkoutLogNotFoundException(logId));
        Reservation reservation = reservationRepository
                .findByIdAndMemberId(log.getReservationId(), memberId)
                .orElseThrow(() -> new WorkoutLogNotFoundException(logId));
        Long workoutPlanId = planDayRepository
                .findById(log.getSuggestedPlanDayId())
                .map(PlanDay::getWorkoutPlanId)
                .orElseThrow(() -> new WorkoutLogNotFoundException(logId));
        List<PlanDay> days = planDayRepository.findByWorkoutPlanIdOrderByOrderIndexAsc(workoutPlanId);

        PlanDay confirmed = resolveConfirmedDay(request, days);
        log.setPlanDayId(confirmed != null ? confirmed.getId() : null);
        log.setFreeTextLabel(confirmed == null ? request.freeTextLabel() : null);
        log.setExercisesLog(request.exercises());
        memberWorkoutLogRepository.save(log);
        return toLogResponse(log, days, reservation.getClassDate());
    }

    // ---- Helpers ----

    private PlanDay resolveConfirmedDay(WorkoutLogSaveRequest request, List<PlanDay> days) {
        if (request.planDayId() == null) {
            if (request.freeTextLabel() == null || request.freeTextLabel().isBlank()) {
                throw new InvalidWorkoutLogException("Hay que elegir un día del plan o escribir qué hizo.");
            }
            return null;
        }
        return days.stream()
                .filter(d -> d.getId().equals(request.planDayId()))
                .findFirst()
                .orElseThrow(() -> new InvalidWorkoutLogException("Ese día no pertenece al plan de este registro."));
    }

    /** La reserva más reciente con check-in real que todavía no tiene log — las más viejas sin
     *  log simplemente quedan sin anotar, no bloquean nada (decisión explícita del diseño). */
    private Reservation findPendingReservation(Long memberId) {
        List<Reservation> checkedIn =
                reservationRepository.findByMemberIdAndCheckedInAtIsNotNullOrderByClassDateDesc(memberId);
        if (checkedIn.isEmpty()) {
            return null;
        }
        Set<Long> alreadyLogged = memberWorkoutLogRepository
                .findByReservationIdIn(checkedIn.stream().map(Reservation::getId).toList())
                .stream()
                .map(MemberWorkoutLog::getReservationId)
                .collect(Collectors.toSet());
        return checkedIn.stream().filter(r -> !alreadyLogged.contains(r.getId())).findFirst().orElse(null);
    }

    /** getNextPlanDay del diseño: sin logs del plan -> día 1; el último log aceptó la sugerencia
     *  -> el siguiente (con wraparound); el último fue texto libre (desvío) -> reinicia a día 1.
     *  "Último" es por fecha de la clase de la reserva, no por id/createdAt (el socio puede
     *  registrar tarde una clase vieja). */
    private PlanDay getNextPlanDay(List<PlanDay> days) {
        MemberWorkoutLog last = latestLog(memberWorkoutLogRepository.findBySuggestedPlanDayIdIn(planDayIds(days)));
        if (last == null || last.getPlanDayId() == null) {
            return days.get(0);
        }
        int lastIndex = indexOf(days, last.getPlanDayId());
        int nextIndex = lastIndex < 0 ? 0 : (lastIndex + 1) % days.size();
        return days.get(nextIndex);
    }

    private MemberWorkoutLog latestLog(List<MemberWorkoutLog> logs) {
        if (logs.isEmpty()) {
            return null;
        }
        Map<Long, LocalDate> classDateByReservationId = reservationRepository
                .findAllById(logs.stream().map(MemberWorkoutLog::getReservationId).toList())
                .stream()
                .collect(Collectors.toMap(Reservation::getId, Reservation::getClassDate));
        return logs.stream()
                .max(Comparator.comparing(l -> classDateByReservationId.get(l.getReservationId())))
                .orElse(null);
    }

    private List<Long> planDayIds(List<PlanDay> days) {
        return days.stream().map(PlanDay::getId).toList();
    }

    private int indexOf(List<PlanDay> days, Long planDayId) {
        for (int i = 0; i < days.size(); i++) {
            if (days.get(i).getId().equals(planDayId)) {
                return i;
            }
        }
        return -1;
    }

    private void requireMember(Long gymId, Long memberId) {
        appUserRepository
                .findByIdAndGymId(memberId, gymId)
                .filter(u -> u.getRole() == Role.MEMBER)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
    }

    private WorkoutPlanResponse toResponse(WorkoutPlan plan) {
        return toResponse(plan, planDayRepository.findByWorkoutPlanIdOrderByOrderIndexAsc(plan.getId()));
    }

    private WorkoutPlanResponse toResponse(WorkoutPlan plan, List<PlanDay> days) {
        return new WorkoutPlanResponse(
                plan.getId(),
                plan.getName(),
                days.stream().map(d -> new PlanDayResponse(d.getId(), d.getOrderIndex(), d.getTitle())).toList());
    }

    private MemberWorkoutLogResponse toLogResponse(MemberWorkoutLog log, List<PlanDay> days) {
        LocalDate classDate = reservationRepository
                .findById(log.getReservationId())
                .map(Reservation::getClassDate)
                .orElse(null);
        return toLogResponse(log, days, classDate);
    }

    private MemberWorkoutLogResponse toLogResponse(MemberWorkoutLog log, List<PlanDay> days, LocalDate classDate) {
        Map<Long, String> titleById = days.stream().collect(Collectors.toMap(PlanDay::getId, PlanDay::getTitle));
        return new MemberWorkoutLogResponse(
                log.getId(),
                log.getReservationId(),
                classDate,
                log.getSuggestedPlanDayId(),
                titleById.get(log.getSuggestedPlanDayId()),
                log.getPlanDayId(),
                log.getPlanDayId() != null ? titleById.get(log.getPlanDayId()) : null,
                log.getFreeTextLabel(),
                log.getExercisesLog(),
                log.getCreatedAt());
    }
}
