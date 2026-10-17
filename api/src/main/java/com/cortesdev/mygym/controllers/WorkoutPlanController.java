package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.dto.MemberWorkoutLogResponse;
import com.cortesdev.mygym.models.dto.WorkoutLogPageResponse;
import com.cortesdev.mygym.models.dto.WorkoutLogSaveRequest;
import com.cortesdev.mygym.models.dto.WorkoutPlanCreateRequest;
import com.cortesdev.mygym.models.dto.WorkoutPlanResponse;
import com.cortesdev.mygym.security.AuthenticatedUser;
import com.cortesdev.mygym.services.WorkoutService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Lado profesor/gym-admin de "Memoria Viva" — ver WorkoutService y SecurityConfig (PROFESOR
 *  tiene escritura acá además de GYM_ADMIN, a diferencia del resto de /api/gym-admin/**). */
@RestController
@RequestMapping("/api/gym-admin/members/{memberId}/workout")
@RequiredArgsConstructor
public class WorkoutPlanController {

    private final WorkoutService workoutService;

    @GetMapping("/plan")
    public WorkoutPlanResponse getActivePlan(@AuthenticationPrincipal Jwt jwt, @PathVariable Long memberId) {
        return workoutService.getActivePlan(AuthenticatedUser.from(jwt).gymId(), memberId);
    }

    @PostMapping("/plan")
    public WorkoutPlanResponse replacePlan(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long memberId,
            @Valid @RequestBody WorkoutPlanCreateRequest request) {
        return workoutService.replacePlan(AuthenticatedUser.from(jwt).gymId(), memberId, request);
    }

    /** Una ventana del historial de registros del socio (7 días por defecto) para el profesor/admin. `before` = cursor
     *  `nextBefore` de la ventana anterior; `days` = tamaño de la ventana (1 a 93). */
    @GetMapping("/logs")
    public WorkoutLogPageResponse getLogHistory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long memberId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate before,
            @RequestParam(required = false) Integer days) {
        return workoutService.getLogHistory(AuthenticatedUser.from(jwt).gymId(), memberId, before, days);
    }

    /** El profesor/admin anota la rutina POR el alumno (reserva con asistencia). Queda guardado quién lo anotó. */
    @PostMapping("/logs/{reservationId}")
    public MemberWorkoutLogResponse createLogForMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long memberId,
            @PathVariable Long reservationId,
            @Valid @RequestBody WorkoutLogSaveRequest request) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        return workoutService.createLogAsStaff(user.gymId(), memberId, reservationId, request, user.userId());
    }

    @PutMapping("/logs/{logId}")
    public MemberWorkoutLogResponse updateLogForMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long memberId,
            @PathVariable Long logId,
            @Valid @RequestBody WorkoutLogSaveRequest request) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        return workoutService.updateLogAsStaff(user.gymId(), memberId, logId, request, user.userId());
    }

    @GetMapping("/latest-log")
    public MemberWorkoutLogResponse getLatestLog(@AuthenticationPrincipal Jwt jwt, @PathVariable Long memberId) {
        return workoutService.getLatestLog(AuthenticatedUser.from(jwt).gymId(), memberId);
    }
}
