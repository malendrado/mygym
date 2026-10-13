package com.cortesdev.mygym.repositories;

import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

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
