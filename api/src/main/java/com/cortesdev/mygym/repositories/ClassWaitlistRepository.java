package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.ClassWaitlistEntry;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassWaitlistRepository extends JpaRepository<ClassWaitlistEntry, Long> {

    List<ClassWaitlistEntry> findByGymBlockIdAndClassDateOrderByCreatedAtAsc(Long gymBlockId, LocalDate classDate);

    Optional<ClassWaitlistEntry> findByGymBlockIdAndClassDateAndMemberId(Long gymBlockId, LocalDate classDate, Long memberId);

    List<ClassWaitlistEntry> findByMemberIdAndClassDateBetween(Long memberId, LocalDate from, LocalDate to);

    // Usado por el cron de escalamiento (WaitlistService.escalateExpiredHeadStarts) — trae TODO
    // lo vigente de una sola vez y agrupa por ocurrencia en memoria, mismo criterio anti-N+1 que
    // ya usa ReservationService en el resto del archivo.
    List<ClassWaitlistEntry> findByClassDateGreaterThanEqual(LocalDate from);

    // Agenda del admin: cuántos esperan en cada ocurrencia del rango, en una sola query.
    List<ClassWaitlistEntry> findByGymBlockIdInAndClassDateBetween(Collection<Long> gymBlockIds, LocalDate from, LocalDate to);

    /** "Mi lista de espera": las esperas de este socio desde `from` en adelante, con los datos del bloque y su lugar en la
     *  fila, todo en UNA consulta (el lugar es un conteo correlacionado que usa idx_class_waitlist_occurrence; antes habría
     *  sido una consulta por entrada). El gym se acota por el bloque (b.gymId) y la fila por memberId: nunca devuelve
     *  entradas de otro socio ni de otro gym. Mismo orden de fila que usa WaitlistService (created_at, desempate por id). */
    @Query("""
            select new com.cortesdev.mygym.repositories.ClassWaitlistRepository$WaitlistRow(
                w.gymBlockId, w.classDate, b.label, b.startTime, b.endTime,
                (select count(o) from ClassWaitlistEntry o
                  where o.gymBlockId = w.gymBlockId and o.classDate = w.classDate
                    and (o.createdAt < w.createdAt or (o.createdAt = w.createdAt and o.id <= w.id))))
            from ClassWaitlistEntry w, GymBlock b
            where b.id = w.gymBlockId and b.gymId = :gymId and w.memberId = :memberId and w.classDate >= :from
            order by w.classDate, b.startTime, w.id
            """)
    List<WaitlistRow> findMyUpcoming(
            @Param("gymId") Long gymId, @Param("memberId") Long memberId, @Param("from") LocalDate from);

    /** Fila de findMyUpcoming. */
    record WaitlistRow(
            Long gymBlockId,
            LocalDate classDate,
            String label,
            java.time.LocalTime startTime,
            java.time.LocalTime endTime,
            Long place) {}
}
