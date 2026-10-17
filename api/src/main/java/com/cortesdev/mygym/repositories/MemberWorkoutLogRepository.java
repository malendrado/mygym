package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.MemberWorkoutLog;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberWorkoutLogRepository extends JpaRepository<MemberWorkoutLog, Long> {

    Optional<MemberWorkoutLog> findByReservationId(Long reservationId);

    List<MemberWorkoutLog> findByReservationIdIn(List<Long> reservationIds);

    /** Para WorkoutService.getNextPlanDay — todo log cuya sugerencia se calculó contra ESTE plan
     *  pertenece a este plan para siempre (se fija en el momento de crear el log, nunca cambia
     *  aunque el socio haya escrito texto libre), ver el comentario largo en MemberWorkoutLog.
     *  Trae TODOS los logs del plan: solo lo usa el tablero "En clase", que carga muchos alumnos en lote. */
    List<MemberWorkoutLog> findBySuggestedPlanDayIdIn(List<Long> planDayIds);

    /** Fecha de la clase más reciente con registro de este socio, estrictamente anterior a `before`. Ancla de cada
     *  ventana del historial (y, con la fecha de la ventana ya cargada, dice si quedan más antiguas). */
    @Query("""
            select max(r.classDate) from MemberWorkoutLog l, Reservation r
            where r.id = l.reservationId and r.memberId = :memberId and r.checkedInAt is not null
              and r.classDate < :before
            """)
    Optional<LocalDate> newestClassDateBefore(@Param("memberId") Long memberId, @Param("before") LocalDate before);

    /** Registros del socio con clase en (bottom, top]: una ventana del historial, más reciente primero. */
    @Query("""
            select new com.cortesdev.mygym.repositories.LoggedClass(l, r.classDate)
            from MemberWorkoutLog l, Reservation r
            where r.id = l.reservationId and r.memberId = :memberId and r.checkedInAt is not null
              and r.classDate <= :top and r.classDate > :bottom
            order by r.classDate desc, l.id desc
            """)
    List<LoggedClass> findWindow(
            @Param("memberId") Long memberId, @Param("top") LocalDate top, @Param("bottom") LocalDate bottom);

    /** El registro más reciente (por fecha de clase) del socio entre los días de UN plan, sin cargar los demás:
     *  pasar `PageRequest.of(0, 1)`. Reemplaza traer todos los logs del plan y quedarse con el máximo en memoria. */
    @Query("""
            select l from MemberWorkoutLog l, Reservation r
            where r.id = l.reservationId and r.memberId = :memberId and r.checkedInAt is not null
              and l.suggestedPlanDayId in :dayIds
            order by r.classDate desc, l.id desc
            """)
    List<MemberWorkoutLog> findNewestOfPlan(
            @Param("memberId") Long memberId, @Param("dayIds") List<Long> dayIds, Pageable pageable);
}
