package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.GymContractCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GymContractCodeRepository extends JpaRepository<GymContractCode, Long> {

    /** Ultimo codigo todavia sin usar del usuario en ese gimnasio: es el unico que puede firmar. */
    Optional<GymContractCode> findFirstByGymIdAndUserIdAndUsedAtIsNullOrderByIdDesc(Long gymId, Long userId);

    List<GymContractCode> findByGymIdAndUserIdAndUsedAtIsNull(Long gymId, Long userId);

    long countByUserIdAndCreatedAtAfter(Long userId, Instant since);

    Optional<GymContractCode> findFirstByUserIdOrderByIdDesc(Long userId);
}
