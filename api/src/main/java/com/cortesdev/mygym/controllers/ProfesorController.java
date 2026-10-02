package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.dto.AdminCreateRequest;
import com.cortesdev.mygym.models.dto.AdminResponse;
import com.cortesdev.mygym.security.AuthenticatedUser;
import com.cortesdev.mygym.services.GymService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lo crea el GYM_ADMIN (nunca el propio PROFESOR) — mismo patrón que MemberService.createMember
 *  para invitar socios, a diferencia de addAdmin que es exclusivo de SUPER_ADMIN bajo /api/gyms.
 *  Ver GymService.addProfesor/listProfesores/removeProfesor. */
@RestController
@RequestMapping("/api/gym-admin/profesores")
@RequiredArgsConstructor
public class ProfesorController {

    private final GymService gymService;

    @GetMapping
    public List<AdminResponse> listProfesores(@AuthenticationPrincipal Jwt jwt) {
        return gymService.listProfesores(AuthenticatedUser.from(jwt).gymId());
    }

    @PostMapping
    public ResponseEntity<AdminResponse> addProfesor(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AdminCreateRequest request) {
        AdminResponse profesor = gymService.addProfesor(AuthenticatedUser.from(jwt).gymId(), request);
        return ResponseEntity.created(URI.create("/api/gym-admin/profesores/" + profesor.id())).body(profesor);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> removeProfesor(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
        gymService.removeProfesor(AuthenticatedUser.from(jwt).gymId(), userId);
        return ResponseEntity.noContent().build();
    }
}
