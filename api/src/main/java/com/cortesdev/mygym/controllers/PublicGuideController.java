package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.dto.GuideResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.security.JwtService;
import com.cortesdev.mygym.services.GymService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Pública (sin sesión) pero no abierta: solo responde con un token de guía firmado y vigente,
 * el que va en el correo de bienvenida. Sin token válido es siempre el mismo 404, no se
 * distingue "vencido" de "falso" ni se expone nada del gym.
 */
@RestController
@RequestMapping("/api/public/guide")
@RequiredArgsConstructor
public class PublicGuideController {

    private final JwtService jwtService;
    private final AppUserRepository appUserRepository;
    private final GymService gymService;

    @GetMapping
    public GuideResponse getGuide(@RequestParam("t") String token) {
        JwtService.GuideClaims claims = jwtService.verifyGuideToken(token);
        if (claims == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        AppUser user = appUserRepository
                .findByIdAndGymId(claims.userId(), claims.gymId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String name = user.getName();
        String firstName = name == null || name.isBlank() ? "" : name.trim().split(" ")[0];
        return new GuideResponse(firstName, gymService.getPublicById(claims.gymId()));
    }
}
