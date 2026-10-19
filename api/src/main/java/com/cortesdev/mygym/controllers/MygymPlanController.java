package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.dto.GymSubscriptionDtos.PlanRequest;
import com.cortesdev.mygym.models.dto.GymSubscriptionDtos.PlanResponse;
import com.cortesdev.mygym.services.MygymPlanService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Planes que mygym le vende a los gimnasios: lectura publica para la landing, gestion solo del super-admin. */
@RestController
@RequiredArgsConstructor
public class MygymPlanController {

    private final MygymPlanService planService;

    /** Alimenta la landing y el FAQ (publico por caer bajo /api/public/**). Cache corta: se actualiza en minutos al editar. */
    @GetMapping("/api/public/mygym-plans")
    public ResponseEntity<List<PlanResponse>> listPublic() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(2)).cachePublic())
                .body(planService.listPublic());
    }

    @GetMapping("/api/mygym-plans")
    public List<PlanResponse> listAll() {
        return planService.listAll();
    }

    @PostMapping("/api/mygym-plans")
    public ResponseEntity<PlanResponse> create(@Valid @RequestBody PlanRequest request) {
        return ResponseEntity.status(201).body(planService.create(request));
    }

    @PutMapping("/api/mygym-plans/{id}")
    public PlanResponse update(@PathVariable Long id, @Valid @RequestBody PlanRequest request) {
        return planService.update(id, request);
    }

    @DeleteMapping("/api/mygym-plans/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        planService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
