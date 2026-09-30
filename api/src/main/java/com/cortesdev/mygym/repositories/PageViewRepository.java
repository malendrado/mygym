package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.PageView;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PageViewRepository extends JpaRepository<PageView, Long> {

    long countByPage(String page);

    long countByPageAndCreatedAtAfter(String page, Instant since);

    /** Reemplaza el viejo findByPageAndGymIdIsNotNull(...) + agregación en memoria Java (traía
     *  TODAS las filas, sin límite) — agrupa en SQL, una sola query. Requiere los índices de
     *  V27 para no caer en un seq scan a medida que crece la tabla. */
    @Query(
            value = """
            SELECT g.id AS gymId, g.name AS gymName, g.slug AS gymSlug,
                   COUNT(*) AS total,
                   COUNT(*) FILTER (WHERE pv.created_at >= :since30d) AS last30d
            FROM page_view pv
            JOIN gym g ON g.id = pv.gym_id
            WHERE pv.page = :page
            GROUP BY g.id, g.name, g.slug
            ORDER BY COUNT(*) DESC
            """,
            nativeQuery = true)
    List<GymVisitAggregateRow> aggregateByGym(@Param("page") String page, @Param("since30d") Instant since30d);
}
