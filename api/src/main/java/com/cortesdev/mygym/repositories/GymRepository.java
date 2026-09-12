package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.Gym;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GymRepository extends JpaRepository<Gym, Long> {

    Optional<Gym> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Gym> findByActive(boolean active);
}
