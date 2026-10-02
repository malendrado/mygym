package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.WorkoutPlan;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutPlanRepository extends JpaRepository<WorkoutPlan, Long> {

    Optional<WorkoutPlan> findByMemberIdAndActiveTrue(Long memberId);
}
