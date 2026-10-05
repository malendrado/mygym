package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.dto.TourProgressRequest;
import com.cortesdev.mygym.security.AuthenticatedUser;
import com.cortesdev.mygym.services.DemoTourService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Beacon del tour guiado de la demo comercial — bajo /api/gym-admin/** (no /api/me/**, que
 *  SecurityConfig restringe a MEMBER) porque quien llama acá es un DEMO_ADMIN, ver el matcher
 *  dedicado agregado en SecurityConfig ANTES del genérico hasRole("GYM_ADMIN"). Fire-and-forget,
 *  nunca debe poder romper el tour que lo dispara. */
@RestController
@RequestMapping("/api/gym-admin/tour-progress")
@RequiredArgsConstructor
public class DemoTourController {

    private final DemoTourService demoTourService;

    @PostMapping
    public ResponseEntity<Void> recordStep(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TourProgressRequest request) {
        demoTourService.recordStepReached(AuthenticatedUser.from(jwt).userId(), request.tour(), request.step());
        return ResponseEntity.noContent().build();
    }
}
