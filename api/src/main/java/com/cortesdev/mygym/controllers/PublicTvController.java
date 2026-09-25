package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.dto.TvPairingCreateResponse;
import com.cortesdev.mygym.models.dto.TvPairingStatusResponse;
import com.cortesdev.mygym.models.dto.TvScheduleResponse;
import com.cortesdev.mygym.services.TvScreenService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sin auth (bajo /api/public/**, permitAll en SecurityConfig) — la TV nunca inicia sesión.
 * Emparejamiento en dos pasos: {@link #createPairing} + {@link #pairingStatus} (polling) hasta
 * que un GYM_ADMIN lo reclama desde su panel (ver TvAdminController); recién ahí
 * {@link #schedule} empieza a devolver datos reales.
 */
@RestController
@RequestMapping("/api/public/tv")
@RequiredArgsConstructor
public class PublicTvController {

    private final TvScreenService tvScreenService;

    @PostMapping("/pairing")
    public TvPairingCreateResponse createPairing() {
        return tvScreenService.createPairingCode();
    }

    @GetMapping("/pairing/{code}")
    public TvPairingStatusResponse pairingStatus(@PathVariable String code) {
        return tvScreenService.pairingStatus(code);
    }

    @GetMapping("/screens/{token}/schedule")
    public TvScheduleResponse schedule(@PathVariable String token) {
        return tvScreenService.getSchedule(token);
    }
}
