package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByGymBlockIdInAndClassDateBetweenAndStatus(
            List<Long> gymBlockIds, LocalDate from, LocalDate to, ReservationStatus status);

    int countByGymBlockIdAndClassDateAndStatus(Long gymBlockId, LocalDate classDate, ReservationStatus status);

    Optional<Reservation> findByGymBlockIdAndClassDateAndMemberIdAndStatus(
            Long gymBlockId, LocalDate classDate, Long memberId, ReservationStatus status);

    Optional<Reservation> findByIdAndMemberId(Long id, Long memberId);

    List<Reservation> findByMemberIdAndStatusOrderByClassDateAsc(Long memberId, ReservationStatus status);
}
