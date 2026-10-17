package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.TvCheckinCode;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TvCheckinCodeRepository extends JpaRepository<TvCheckinCode, Long> {

    Optional<TvCheckinCode> findByCode(String code);

    /** Limpieza diaria (ExpiredRowsPurgeJob): nadie más borra estos códigos. */
    long deleteByExpiresAtBefore(Instant cutoff);
}
