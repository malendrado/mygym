package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.dto.MemberWorkoutLogResponse;
import com.cortesdev.mygym.models.dto.PendingWorkoutResponse;
import com.cortesdev.mygym.models.dto.WorkoutLogSaveRequest;
import com.cortesdev.mygym.security.AuthenticatedUser;
import com.cortesdev.mygym.services.WorkoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Autoservicio del socio en la pestaña "Rutina" de /member — ver WorkoutService. */
@RestController
@RequestMapping("/api/me/workout")
@RequiredArgsConstructor
public class WorkoutLogController {

    private final WorkoutService workoutService;

    @GetMapping("/pending")
    public PendingWorkoutResponse getPending(@AuthenticationPrincipal Jwt jwt) {
        return workoutService.getPendingWorkout(AuthenticatedUser.from(jwt).userId());
    }

    @PostMapping("/logs/{reservationId}")
    public MemberWorkoutLogResponse createLog(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long reservationId,
            @Valid @RequestBody WorkoutLogSaveRequest request) {
        return workoutService.createLog(AuthenticatedUser.from(jwt).userId(), reservationId, request);
    }

    @PutMapping("/logs/{logId}")
    public MemberWorkoutLogResponse updateLog(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long logId, @Valid @RequestBody WorkoutLogSaveRequest request) {
        return workoutService.updateLog(AuthenticatedUser.from(jwt).userId(), logId, request);
    }
}
