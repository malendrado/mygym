package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.dto.RecordVisitRequest;
import com.cortesdev.mygym.services.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/analytics")
@RequiredArgsConstructor
public class PublicAnalyticsController {

    private final AnalyticsService analyticsService;

    /** Beacon fire-and-forget disparado por el brochure, la landing y /j/{slug} — nunca debe
     *  fallarle a quien lo llama, por eso siempre responde 204 sin importar el contenido. */
    @PostMapping("/visit")
    public ResponseEntity<Void> recordVisit(@RequestBody RecordVisitRequest request) {
        analyticsService.recordVisit(request.page(), request.gymSlug());
        return ResponseEntity.noContent().build();
    }
}
