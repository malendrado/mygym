package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.dto.AnalyticsSummaryResponse;
import com.cortesdev.mygym.services.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Bajo /api/gyms/** — mismo mecanismo de SecurityConfig que protege GymController entero con
 *  hasRole("SUPER_ADMIN"), sin necesidad de @PreAuthorize (el proyecto no usa esa anotación). */
@RestController
@RequestMapping("/api/gyms/analytics")
@RequiredArgsConstructor
public class GymAnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    public AnalyticsSummaryResponse getSummary() {
        return analyticsService.getSummary();
    }
}
