package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.WorkoutPlan;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutPlanRepository extends JpaRepository<WorkoutPlan, Long> {

    Optional<WorkoutPlan> findByMemberIdAndActiveTrue(Long memberId);

    /** Batch para el tablero "En clase": los planes activos de todos los alumnos de una clase en una consulta. */
    java.util.List<WorkoutPlan> findByMemberIdInAndActiveTrue(java.util.Collection<Long> memberIds);
}
