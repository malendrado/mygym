package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.GymClosure;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GymClosureRepository extends JpaRepository<GymClosure, Long> {

    /** Cierres cuyo rango CRUDO todavía no terminó (endDate >= from) — para el banner de /member, que
     *  no necesita el historial completo; el corte fino por effectiveEndDate() se hace en memoria. */
    List<GymClosure> findByGymIdAndEndDateGreaterThanEqual(Long gymId, LocalDate from);

    /** Historial de cierres de un gym, del más reciente al más antiguo, paginado. */
    Page<GymClosure> findByGymId(Long gymId, Pageable pageable);

    Optional<GymClosure> findByIdAndGymId(Long id, Long gymId);

    /** Candidatos cuyo rango CRUDO (sin considerar un levantamiento anticipado) se solapa con
     *  [from, to] — el filtro fino por effectiveEndDate() se hace en memoria (ver GymClosureService). */
    List<GymClosure> findByGymIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long gymId, LocalDate to, LocalDate from);
}
