package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.dto.MygymSettingsDtos.Request;
import com.cortesdev.mygym.models.dto.MygymSettingsDtos.Response;
import com.cortesdev.mygym.security.AuthenticatedUser;
import com.cortesdev.mygym.services.MygymSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Datos de mygym como empresa, para el panel de super admin (todo /api/mygym-settings/** exige SUPER_ADMIN). */
@RestController
@RequestMapping("/api/mygym-settings")
@RequiredArgsConstructor
public class MygymSettingsController {

    private final MygymSettingsService service;

    @GetMapping
    public Response get() {
        return service.current();
    }

    @PutMapping
    public Response update(@Valid @RequestBody Request request, @AuthenticationPrincipal Jwt jwt) {
        return service.update(request, AuthenticatedUser.from(jwt).userId());
    }
}
