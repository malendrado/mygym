package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.MygymPlan;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MygymPlanRepository extends JpaRepository<MygymPlan, Long> {

    List<MygymPlan> findByActiveTrueOrderBySortOrderAscIdAsc();

    List<MygymPlan> findAllByOrderBySortOrderAscIdAsc();

    boolean existsByCode(String code);

    Optional<MygymPlan> findByCode(String code);
}
