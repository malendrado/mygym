package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.ClassWaitlistEntry;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassWaitlistRepository extends JpaRepository<ClassWaitlistEntry, Long> {

    List<ClassWaitlistEntry> findByGymBlockIdAndClassDateOrderByCreatedAtAsc(Long gymBlockId, LocalDate classDate);

    Optional<ClassWaitlistEntry> findByGymBlockIdAndClassDateAndMemberId(Long gymBlockId, LocalDate classDate, Long memberId);

    List<ClassWaitlistEntry> findByMemberIdAndClassDateBetween(Long memberId, LocalDate from, LocalDate to);

    // Usado por el cron de escalamiento (WaitlistService.escalateExpiredHeadStarts) — trae TODO
    // lo vigente de una sola vez y agrupa por ocurrencia en memoria, mismo criterio anti-N+1 que
    // ya usa ReservationService en el resto del archivo.
    List<ClassWaitlistEntry> findByClassDateGreaterThanEqual(LocalDate from);
}
