package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.PageView;
import com.cortesdev.mygym.models.dto.AnalyticsSummaryResponse;
import com.cortesdev.mygym.models.dto.GymVisitStats;
import com.cortesdev.mygym.models.dto.PageStats;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.PageViewRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Contador propio de visitas a las 3 superficies públicas del producto (brochure, landing,
 *  y la página de alta de cada gimnasio) — reemplaza la necesidad de leer analytics de
 *  terceros (Vercel Web Analytics no expone API pública para esto). Best-effort a propósito:
 *  registrar una visita nunca debe poder romper la página que la dispara. */
@Service
@RequiredArgsConstructor
@Transactional
public class AnalyticsService {

    private static final Set<String> ALLOWED_PAGES = Set.of("BROCHURE", "LANDING", "JOIN");

    private final PageViewRepository pageViewRepository;
    private final GymRepository gymRepository;

    public void recordVisit(String page, String gymSlug) {
        if (page == null || !ALLOWED_PAGES.contains(page)) {
            return;
        }
        Long gymId = null;
        if ("JOIN".equals(page) && gymSlug != null && !gymSlug.isBlank()) {
            gymId = gymRepository.findBySlug(gymSlug).map(Gym::getId).orElse(null);
        }
        pageViewRepository.save(PageView.builder().page(page).gymId(gymId).build());
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse getSummary() {
        Instant now = Instant.now();
        Instant since7d = now.minus(7, ChronoUnit.DAYS);
        Instant since30d = now.minus(30, ChronoUnit.DAYS);

        PageStats brochure = statsFor("BROCHURE", since7d, since30d);
        PageStats landing = statsFor("LANDING", since7d, since30d);
        PageStats joinTotal = statsFor("JOIN", since7d, since30d);

        Map<Long, Long> totalByGym = pageViewRepository.findByPageAndGymIdIsNotNull("JOIN").stream()
                .collect(Collectors.groupingBy(PageView::getGymId, Collectors.counting()));
        Map<Long, Long> last30dByGym = pageViewRepository
                .findByPageAndGymIdIsNotNullAndCreatedAtAfter("JOIN", since30d)
                .stream()
                .collect(Collectors.groupingBy(PageView::getGymId, Collectors.counting()));

        List<GymVisitStats> byGym = new ArrayList<>(gymRepository.findAll().stream()
                .filter(gym -> totalByGym.containsKey(gym.getId()))
                .map(gym -> new GymVisitStats(
                        gym.getId(),
                        gym.getName(),
                        gym.getSlug(),
                        totalByGym.getOrDefault(gym.getId(), 0L),
                        last30dByGym.getOrDefault(gym.getId(), 0L)))
                .sorted(Comparator.comparingLong(GymVisitStats::total).reversed())
                .toList());

        // El total de joinTotal cuenta TODAS las visitas a /j/:slug, incluidas las de un slug
        // que no coincidió con ningún gimnasio (link roto, gimnasio borrado, typo) — esas nunca
        // quedan agrupadas en totalByGym/last30dByGym porque no tienen gymId. Sin esta fila, el
        // número agregado de la tarjeta no reconciliaba con la suma de la lista de abajo.
        long unresolvedTotal = joinTotal.total() - totalByGym.values().stream().mapToLong(Long::longValue).sum();
        long unresolvedLast30d = joinTotal.last30d() - last30dByGym.values().stream().mapToLong(Long::longValue).sum();
        if (unresolvedTotal > 0) {
            byGym.add(new GymVisitStats(null, "Otros (link sin gimnasio)", null, unresolvedTotal, unresolvedLast30d));
        }

        return new AnalyticsSummaryResponse(brochure, landing, joinTotal, byGym);
    }

    private PageStats statsFor(String page, Instant since7d, Instant since30d) {
        long total = pageViewRepository.countByPage(page);
        long last7d = pageViewRepository.countByPageAndCreatedAtAfter(page, since7d);
        long last30d = pageViewRepository.countByPageAndCreatedAtAfter(page, since30d);
        return new PageStats(total, last7d, last30d);
    }
}
