package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.dto.GymSummaryResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
    @Query("SELECT new com.cortesdev.mygym.models.dto.GymSummaryResponse("
            + "g.id, g.publicId, g.name, g.slug, g.active, g.maxUsers, g.themeColor, g.logoSvg) "
            + "FROM Gym g WHERE (:active IS NULL OR g.active = :active) ORDER BY g.name")
    List<GymSummaryResponse> findAllSummaries(@Param("active") Boolean active);
}
