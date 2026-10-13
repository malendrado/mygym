package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.PlanDay;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanDayRepository extends JpaRepository<PlanDay, Long> {

    List<PlanDay> findByWorkoutPlanIdOrderByOrderIndexAsc(Long workoutPlanId);

    List<PlanDay> findByWorkoutPlanIdInOrderByOrderIndexAsc(java.util.Collection<Long> workoutPlanIds);
}
