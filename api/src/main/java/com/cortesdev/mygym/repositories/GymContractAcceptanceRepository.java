package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.GymContractAcceptance;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GymContractAcceptanceRepository extends JpaRepository<GymContractAcceptance, Long> {

    boolean existsByGymIdAndContractVersion(Long gymId, String contractVersion);

    Optional<GymContractAcceptance> findFirstByGymIdAndContractVersionOrderByIdDesc(Long gymId, String contractVersion);

    Optional<GymContractAcceptance> findFirstByGymIdOrderByIdDesc(Long gymId);
}
