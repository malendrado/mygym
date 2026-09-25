package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.TvScreen;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TvScreenRepository extends JpaRepository<TvScreen, Long> {

    List<TvScreen> findByGymId(Long gymId);

    Optional<TvScreen> findByIdAndGymId(Long id, Long gymId);

    Optional<TvScreen> findByToken(String token);
}
