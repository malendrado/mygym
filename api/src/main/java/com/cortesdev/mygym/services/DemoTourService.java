package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.DemoTourProgress;
import com.cortesdev.mygym.repositories.DemoTourProgressRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Progreso del tour guiado de la demo comercial — best-effort, igual que AnalyticsService:
 *  registrar un paso nunca debe poder romper el tour que lo dispara. */
@Service
@RequiredArgsConstructor
@Transactional
public class DemoTourService {

    private static final Set<String> ALLOWED_TOURS = Set.of("ADMIN", "MEMBER");

    private final DemoTourProgressRepository repository;

    public void recordStepReached(Long demoAdminId, String tour, int step) {
        if (demoAdminId == null || tour == null || !ALLOWED_TOURS.contains(tour) || step < 1) {
            return;
        }
        DemoTourProgress progress = repository
                .findByDemoAdminIdAndTour(demoAdminId, tour)
                .orElseGet(() -> DemoTourProgress.builder()
                        .demoAdminId(demoAdminId)
                        .tour(tour)
                        .maxStep(0)
                        .build());
        if (step <= progress.getMaxStep()) {
            return;
        }
        progress.setMaxStep(step);
        progress.setReachedAt(java.time.Instant.now());
        repository.save(progress);
    }

    /** Para el listado "Acceso a la demo" del super-admin — un solo round-trip para todos los
     *  admins de la tarjeta, agrupado por demoAdminId con ambos tours adentro. */
    @Transactional(readOnly = true)
    public Map<Long, List<DemoTourProgress>> findProgressByAdminIds(List<Long> demoAdminIds) {
        if (demoAdminIds.isEmpty()) {
            return Map.of();
        }
        return repository.findByDemoAdminIdIn(demoAdminIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(DemoTourProgress::getDemoAdminId));
    }
}
