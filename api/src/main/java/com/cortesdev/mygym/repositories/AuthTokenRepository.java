package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.AuthToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    Optional<AuthToken> findByTokenHash(String tokenHash);

    /** Limpieza diaria (ExpiredRowsPurgeJob): invitaciones, reseteos y altas vencidos. */
    long deleteByExpiresAtBefore(Instant cutoff);
}
