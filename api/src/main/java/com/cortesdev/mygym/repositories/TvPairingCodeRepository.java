package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.TvPairingCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TvPairingCodeRepository extends JpaRepository<TvPairingCode, Long> {

    Optional<TvPairingCode> findByCode(String code);
}
