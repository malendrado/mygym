package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.GymPlan;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GymPlanRepository extends JpaRepository<GymPlan, Long> {

    List<GymPlan> findByGymId(Long gymId);

    Optional<GymPlan> findByIdAndGymId(Long id, Long gymId);
}
