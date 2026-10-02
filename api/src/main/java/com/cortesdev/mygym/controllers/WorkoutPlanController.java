package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.dto.MemberWorkoutLogResponse;
import com.cortesdev.mygym.models.dto.WorkoutPlanCreateRequest;
import com.cortesdev.mygym.models.dto.WorkoutPlanResponse;
import com.cortesdev.mygym.security.AuthenticatedUser;
import com.cortesdev.mygym.services.WorkoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @GetMapping("/latest-log")
    public MemberWorkoutLogResponse getLatestLog(@AuthenticationPrincipal Jwt jwt, @PathVariable Long memberId) {
        return workoutService.getLatestLog(AuthenticatedUser.from(jwt).gymId(), memberId);
    }
}
