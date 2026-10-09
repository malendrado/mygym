package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.dto.GymSummaryResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GymRepository extends JpaRepository<Gym, Long> {

    Optional<Gym> findBySlug(String slug);

    Optional<Gym> findByPublicId(UUID publicId);

    boolean existsBySlug(String slug);

    List<Gym> findByActive(boolean active);

    /** Proyección liviana para /admin/gyms — nunca hidrata el Gym completo (evita traer y
     *  descifrar flowApiKey/flowSecretKey y el resto de columnas que esa lista no usa). */
    @Query(
            value = "SELECT new com.cortesdev.mygym.models.dto.GymSummaryResponse("
                    + "g.id, g.publicId, g.name, g.slug, g.active, g.maxUsers, g.themeColor, g.logoSvg) "
                    + "FROM Gym g WHERE (:active IS NULL OR g.active = :active) "
                    + "AND (LOWER(g.name) LIKE :pattern OR LOWER(g.slug) LIKE :pattern) ORDER BY g.name, g.id",
            countQuery = "SELECT COUNT(g) FROM Gym g WHERE (:active IS NULL OR g.active = :active) "
                    + "AND (LOWER(g.name) LIKE :pattern OR LOWER(g.slug) LIKE :pattern)")
    Page<GymSummaryResponse> findSummariesPage(
            @Param("active") Boolean active, @Param("pattern") String pattern, Pageable pageable);

    long countByActive(boolean active);

    @Query("SELECT COALESCE(SUM(g.maxUsers), 0) FROM Gym g")
    long sumMaxUsers();

    @Query("SELECT COUNT(g) FROM Gym g WHERE g.logoSvg IS NOT NULL AND g.logoSvg <> ''")
    long countBranded();
}
