package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.MemberWorkoutLog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberWorkoutLogRepository extends JpaRepository<MemberWorkoutLog, Long> {

    Optional<MemberWorkoutLog> findByReservationId(Long reservationId);

    List<MemberWorkoutLog> findByReservationIdIn(List<Long> reservationIds);

    /** Para WorkoutService.getNextPlanDay — todo log cuya sugerencia se calculó contra ESTE plan
     *  pertenece a este plan para siempre (se fija en el momento de crear el log, nunca cambia
     *  aunque el socio haya escrito texto libre), ver el comentario largo en MemberWorkoutLog. */
    List<MemberWorkoutLog> findBySuggestedPlanDayIdIn(List<Long> planDayIds);
}
