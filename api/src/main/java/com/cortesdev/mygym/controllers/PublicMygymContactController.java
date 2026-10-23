package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.dto.MygymSettingsDtos.PublicContact;
import com.cortesdev.mygym.services.MygymSettingsService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Contacto de mygym (WhatsApp y correo) para la landing, el FAQ, la política de privacidad y los avisos. Solo esos dos datos. */
@RestController
@RequestMapping("/api/public/mygym-contact")
@RequiredArgsConstructor
public class PublicMygymContactController {

    private final MygymSettingsService service;

    @GetMapping
    public ResponseEntity<PublicContact> get() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic()).body(service.publicContact());
    }
}
