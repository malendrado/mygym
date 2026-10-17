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

    /** Para el control de pausa por peticion (GymPauseGuard): una consulta liviana, sin traer el logo ni las llaves. */
    boolean existsByIdAndActiveTrue(Long id);

    /**
     * Gimnasio "liviano" (ver el constructor de solo lectura de Gym): lo que necesitan reservar, cancelar, el calendario, la
     * lista de espera y los avisos. Evita traer el logo y descifrar las llaves de Flow en cada reserva. No guardar el resultado.
     */
    @org.springframework.data.jpa.repository.Query("SELECT new com.cortesdev.mygym.models.Gym("
            + "g.id, g.name, g.slug, g.active, g.themeColor, g.bookingWindowMinutes, g.cancellationWindowMinutes, "
            + "g.waitlistHeadStartMinutes, g.showAttendeesToMembers, g.fixedMembersCurrentWeekOnly) FROM Gym g WHERE g.id = :id")
    java.util.Optional<Gym> findSettingsViewById(@org.springframework.data.repository.query.Param("id") Long id);

    /** Solo el cupo de socios: los indicadores del panel no necesitan traer el gimnasio entero (logo en base64 incluido). */
    @org.springframework.data.jpa.repository.Query("SELECT g.maxUsers FROM Gym g WHERE g.id = :id")
    java.util.Optional<Integer> findMaxUsersById(@org.springframework.data.repository.query.Param("id") Long id);

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
