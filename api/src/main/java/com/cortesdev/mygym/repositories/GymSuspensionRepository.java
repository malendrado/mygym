package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.GymSuspension;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GymSuspensionRepository extends JpaRepository<GymSuspension, Long> {

    Optional<GymSuspension> findFirstByGymIdAndEndedAtIsNullOrderByIdDesc(Long gymId);
}
