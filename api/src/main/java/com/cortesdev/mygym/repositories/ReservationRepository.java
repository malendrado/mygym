package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** Cuantas reservas vigentes (BOOKED) hay desde `from` en adelante y cual es la mas nueva: cambia al reservar o cancelar. */
    interface BookedStamp {
        long getTotal();

        Long getLastId();
    }

    /** Marca barata del estado de las reservas de un gimnasio (GymStatusController): una sola consulta por idx_reservation_block_date. */
    @Query("select count(r) as total, max(r.id) as lastId from Reservation r "
            + "where r.status = :status and r.classDate >= :from "
            + "and r.gymBlockId in (select b.id from GymBlock b where b.gymId = :gymId)")
    BookedStamp bookedStamp(
            @Param("gymId") Long gymId, @Param("from") LocalDate from, @Param("status") ReservationStatus status);

    default BookedStamp bookedStamp(Long gymId, LocalDate from) {
        return bookedStamp(gymId, from, ReservationStatus.BOOKED);
    }

    List<Reservation> findByGymBlockIdInAndClassDateBetweenAndStatus(
            List<Long> gymBlockIds, LocalDate from, LocalDate to, ReservationStatus status);

    int countByGymBlockIdAndClassDateAndStatus(Long gymBlockId, LocalDate classDate, ReservationStatus status);

    /** Aviso al equipo: cuantos socios cancelaron esta clase por su cuenta (no por un cierre) desde "since". */
    int countByGymBlockIdAndClassDateAndStatusAndCancelledByClosureIdIsNullAndUpdatedAtAfter(
            Long gymBlockId, LocalDate classDate, ReservationStatus status, java.time.Instant since);

    /** Cron de recordatorios (ClassReminderJob): reservas vigentes de un dia que todavia no se evaluaron. */
    List<Reservation> findByClassDateAndStatusAndReminderPushedAtIsNull(LocalDate classDate, ReservationStatus status);

    Optional<Reservation> findByGymBlockIdAndClassDateAndMemberIdAndStatus(
            Long gymBlockId, LocalDate classDate, Long memberId, ReservationStatus status);

    Optional<Reservation> findByIdAndMemberId(Long id, Long memberId);

    /** Misión de bienvenida: ¿alguna vez reservó (aunque después cancelara)? */
    boolean existsByMemberId(Long memberId);

    /** Misión de bienvenida: ¿alguna vez hizo check-in? */
    boolean existsByMemberIdAndCheckedInAtIsNotNull(Long memberId);

    /** "Mis reservas · Próximas": hoy en adelante, completo (acotado por la ventana de reserva). */
    List<Reservation> findByMemberIdAndStatusAndClassDateGreaterThanEqualOrderByClassDateAsc(
            Long memberId, ReservationStatus status, LocalDate from);

    /** "Mis reservas · Pasadas": todo lo anterior a hoy, paginado (crece sin límite con el uso). */
    Page<Reservation> findByMemberIdAndStatusAndClassDateLessThan(
            Long memberId, ReservationStatus status, LocalDate before, Pageable pageable);

    List<Reservation> findByMemberIdAndStatusAndClassDateGreaterThanEqual(
            Long memberId, ReservationStatus status, LocalDate from);

    List<Reservation> findByMemberIdInAndStatusAndClassDateGreaterThanEqual(
            List<Long> memberIds, ReservationStatus status, LocalDate from);

    int countByMemberIdAndStatusAndClassDateBetween(Long memberId, ReservationStatus status, LocalDate from, LocalDate to);

    List<Reservation> findByGymBlockIdAndClassDateAndStatus(Long gymBlockId, LocalDate classDate, ReservationStatus status);

    /** Reservas vigentes de una clase desde `from` en adelante: a quienes avisar si cambia de horario o se cancela la clase. */
    List<Reservation> findByGymBlockIdAndStatusAndClassDateGreaterThanEqual(Long gymBlockId, ReservationStatus status, LocalDate from);

    List<Reservation> findByMemberIdIn(List<Long> memberIds);

    /** Todas las reservas del socio en un rango, de cualquier estado — la generación de clases
     *  fijas necesita ver también las CANCELLED para no re-crear una fecha que el socio soltó. */
    List<Reservation> findByMemberIdAndClassDateBetween(Long memberId, LocalDate from, LocalDate to);

    List<Reservation> findByMemberIdAndClassDateAndStatus(Long memberId, LocalDate classDate, ReservationStatus status);

    int countByMemberIdIn(List<Long> memberIds);

    /** Batch para MemberService.toResponses — una sola query para calcular sessionsRemaining
     *  de una lista completa de socios, en vez de un count() por socio (ver el comentario
     *  largo en MemberService). El rango from/to es el más amplio necesario entre todos los
     *  socios pedidos; el filtrado al período exacto de cada uno se hace en memoria. */
    List<Reservation> findByMemberIdInAndStatusAndClassDateBetween(
            List<Long> memberIds, ReservationStatus status, LocalDate from, LocalDate to);

    /** Para WorkoutService.getPendingWorkout — candidatas a Memoria Viva (asistió de verdad).
     *  El volumen por socio es chico, se filtra en memoria cuál es la más reciente sin log
     *  todavía (ver WorkoutService), mismo criterio que el resto del proyecto para listas
     *  acotadas por socio. */
    List<Reservation> findByMemberIdAndCheckedInAtIsNotNullOrderByClassDateDesc(Long memberId);

    /** La clase más reciente con asistencia que todavía no tiene registro de rutina: pasar `PageRequest.of(0, 1)`.
     *  Una sola consulta que se detiene en la primera, en vez de traer todas las asistencias del socio. */
    @Query("""
            select r from Reservation r
            where r.memberId = :memberId and r.checkedInAt is not null
              and not exists (select 1 from MemberWorkoutLog l where l.reservationId = r.id)
            order by r.classDate desc, r.id desc
            """)
    List<Reservation> findPendingWorkoutCandidates(@Param("memberId") Long memberId, Pageable pageable);

    /** Tablero "En clase": reservas con asistencia de varios socios desde una fecha — una sola consulta para toda
     *  la clase, acotada por `from` (los últimos días), nunca todo el historial. */
    List<Reservation> findByMemberIdInAndCheckedInAtIsNotNullAndClassDateGreaterThanEqual(
            java.util.Collection<Long> memberIds, LocalDate from);

    /** Tablero "En clase": cuáles de estos socios ya habían reservado alguna clase antes de `before`
     *  (los que no aparecen acá van a su primera clase). */
    @org.springframework.data.jpa.repository.Query(
            "select distinct r.memberId from Reservation r where r.memberId in :memberIds"
                    + " and r.status = com.cortesdev.mygym.models.ReservationStatus.BOOKED and r.classDate < :before")
    List<Long> findMemberIdsWithBookingBefore(
            @org.springframework.data.repository.query.Param("memberIds") java.util.Collection<Long> memberIds,
            @org.springframework.data.repository.query.Param("before") LocalDate before);

    /** Para ClosureNotificationService — todas las reservas que un cierre puntual canceló. */
    List<Reservation> findByCancelledByClosureId(Long closureId);
}
