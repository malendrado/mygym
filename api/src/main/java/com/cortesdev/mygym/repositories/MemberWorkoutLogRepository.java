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

    /** Por cada plan, el día que el socio hizo en su registro MÁS reciente (por fecha de la clase) entre los días de ese plan.
     *  Una sola consulta para todo el tablero "En clase": antes se traían TODOS los registros de cada plan, con el JSONB de
     *  ejercicios, y sus reservas, y crecía con el historial. Aquí Postgres se queda con una fila por plan y no lee los ejercicios.
     *  Un registro de texto libre (sin día de plan) devuelve planDayId = null. */
    @Query(value = """
            SELECT DISTINCT ON (pd.workout_plan_id) pd.workout_plan_id AS planId, l.plan_day_id AS planDayId
            FROM member_workout_log l
            JOIN reservation r ON r.id = l.reservation_id
            JOIN plan_day pd ON pd.id = l.suggested_plan_day_id
            WHERE pd.workout_plan_id IN (:planIds)
            ORDER BY pd.workout_plan_id, r.class_date DESC, l.id DESC
            """, nativeQuery = true)
    List<LatestPlanLog> findLatestPlanDayByPlan(@Param("planIds") List<Long> planIds);

    /** Fila de findLatestPlanDayByPlan. */
    interface LatestPlanLog {
        Long getPlanId();

        Long getPlanDayId();
    }

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
