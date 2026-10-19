package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.GymSubscription;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GymSubscriptionRepository extends JpaRepository<GymSubscription, Long> {

    /** Con la fila bloqueada: dos pagos del mismo gimnasio a la vez se aplican uno tras otro, sin perder un mes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GymSubscription> findLockedByGymId(Long gymId);

    /** Para el control de pausa por peticion (GymPauseGuard): una consulta liviana. */
    @Query("SELECT s.billingStatus FROM GymSubscription s WHERE s.gymId = :gymId")
    Optional<String> findBillingStatusByGymId(@Param("gymId") Long gymId);

    List<GymSubscription> findByExemptFalse();

    boolean existsByPlanId(Long planId);

    /** Limites de profesores y administradores que fija el plan del gimnasio (vacio si no tiene plan). */
    @Query("SELECT p.maxProfesores FROM GymSubscription s JOIN MygymPlan p ON p.id = s.planId WHERE s.gymId = :gymId")
    Optional<Integer> findMaxProfesoresByGymId(@Param("gymId") Long gymId);

    @Query("SELECT p.maxAdmins FROM GymSubscription s JOIN MygymPlan p ON p.id = s.planId WHERE s.gymId = :gymId")
    Optional<Integer> findMaxAdminsByGymId(@Param("gymId") Long gymId);
}
