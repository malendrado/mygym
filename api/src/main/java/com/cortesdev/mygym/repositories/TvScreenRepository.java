package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.TvScreen;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TvScreenRepository extends JpaRepository<TvScreen, Long> {

    List<TvScreen> findByGymId(Long gymId);

    Optional<TvScreen> findByIdAndGymId(Long id, Long gymId);

    Optional<TvScreen> findByToken(String token);

    /** Marca de "la pantalla sigue viva" sin cargar y reescribir la fila entera (un UPDATE directo, sin el SELECT del merge). */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE TvScreen s SET s.lastPolledAt = :at WHERE s.id = :id")
    int touchLastPolledAt(@org.springframework.data.repository.query.Param("id") Long id, @org.springframework.data.repository.query.Param("at") java.time.Instant at);
}
