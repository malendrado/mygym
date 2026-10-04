package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.GymClosure;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GymClosureRepository extends JpaRepository<GymClosure, Long> {

    List<GymClosure> findByGymIdOrderByCreatedAtDesc(Long gymId);

    Optional<GymClosure> findByIdAndGymId(Long id, Long gymId);

    /** Candidatos cuyo rango CRUDO (sin considerar un levantamiento anticipado) se solapa con
     *  [from, to] — el filtro fino por effectiveEndDate() se hace en memoria (ver GymClosureService). */
    List<GymClosure> findByGymIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long gymId, LocalDate to, LocalDate from);
}
