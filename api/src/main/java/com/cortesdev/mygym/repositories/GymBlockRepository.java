package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.GymBlock;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GymBlockRepository extends JpaRepository<GymBlock, Long> {

    List<GymBlock> findByGymId(Long gymId);

    Optional<GymBlock> findByIdAndGymId(Long id, Long gymId);
}
