package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.dto.GymBlockOccurrenceResponse;
import com.cortesdev.mygym.models.dto.ReservationCreateRequest;
import com.cortesdev.mygym.models.dto.ReservationResponse;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.services.exception.BookingWindowClosedException;
import com.cortesdev.mygym.services.exception.CapacityExceededException;
import com.cortesdev.mygym.services.exception.GymBlockNotFoundException;
import com.cortesdev.mygym.services.exception.GymNotFoundException;
import com.cortesdev.mygym.services.exception.ReservationNotFoundException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ReservationService {

    private static final ZoneId GYM_ZONE = ZoneId.of("America/Santiago");

    private final GymBlockRepository gymBlockRepository;
    private final ReservationRepository reservationRepository;
    private final GymRepository gymRepository;

    @Transactional(readOnly = true)
    public List<GymBlockOccurrenceResponse> listOccurrences(Long gymId, Long memberId, LocalDate from, LocalDate to) {
        int cancellationWindowHours = findGymOrThrow(gymId).getCancellationWindowHours();
        List<GymBlock> blocks =
                gymBlockRepository.findByGymId(gymId).stream().filter(GymBlock::isActive).toList();
        List<Long> blockIds = blocks.stream().map(GymBlock::getId).toList();
        List<Reservation> myReservations = blockIds.isEmpty()
                ? List.of()
                : reservationRepository
                        .findByGymBlockIdInAndClassDateBetweenAndStatus(blockIds, from, to, ReservationStatus.BOOKED)
                        .stream()
                        .filter(r -> r.getMemberId().equals(memberId))
                        .toList();

        List<GymBlockOccurrenceResponse> result = new ArrayList<>();
        for (GymBlock block : blocks) {
            for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                if (date.getDayOfWeek() != block.getDayOfWeek()) {
                    continue;
                }
                LocalDate occurrenceDate = date;
                int taken = reservationRepository.countByGymBlockIdAndClassDateAndStatus(
                        block.getId(), occurrenceDate, ReservationStatus.BOOKED);
                boolean bookable = taken < block.getCapacity()
                        && isWithinBookingWindow(cancellationWindowHours, occurrenceDate, block.getStartTime());
                Long myReservationId = myReservations.stream()
                        .filter(r -> r.getGymBlockId().equals(block.getId())
                                && r.getClassDate().equals(occurrenceDate))
                        .map(Reservation::getId)
                        .findFirst()
                        .orElse(null);
                result.add(new GymBlockOccurrenceResponse(
                        block.getId(),
                        block.getLabel(),
                        occurrenceDate,
                        block.getDayOfWeek(),
                        block.getStartTime(),
                        block.getEndTime(),
                        block.getCapacity(),
                        block.getCategory(),
                        block.getInstructorName(),
                        block.getInstructorPhoto(),
                        taken,
                        bookable,
                        myReservationId));
            }
        }
        result.sort(Comparator.comparing(GymBlockOccurrenceResponse::classDate)
                .thenComparing(GymBlockOccurrenceResponse::startTime));
        return result;
    }

    public ReservationResponse book(Long gymId, Long memberId, ReservationCreateRequest request) {
        int cancellationWindowHours = findGymOrThrow(gymId).getCancellationWindowHours();
        GymBlock block = gymBlockRepository
                .findByIdAndGymId(request.gymBlockId(), gymId)
                .filter(GymBlock::isActive)
                .orElseThrow(() -> new GymBlockNotFoundException(gymId, request.gymBlockId()));

        if (request.classDate().getDayOfWeek() != block.getDayOfWeek()) {
            throw new BookingWindowClosedException("La fecha elegida no coincide con el día de la semana de este bloque");
        }
        requireWithinBookingWindow(cancellationWindowHours, request.classDate(), block.getStartTime());

        reservationRepository
                .findByGymBlockIdAndClassDateAndMemberIdAndStatus(
                        block.getId(), request.classDate(), memberId, ReservationStatus.BOOKED)
                .ifPresent(r -> {
                    throw new BookingWindowClosedException("Ya tienes una reserva para esta clase");
                });

        int taken = reservationRepository.countByGymBlockIdAndClassDateAndStatus(
                block.getId(), request.classDate(), ReservationStatus.BOOKED);
        if (taken >= block.getCapacity()) {
            throw new CapacityExceededException(block.getId());
        }

        Reservation reservation = Reservation.builder()
                .gymBlockId(block.getId())
                .memberId(memberId)
                .classDate(request.classDate())
                .status(ReservationStatus.BOOKED)
                .build();
        return toResponse(reservationRepository.save(reservation), block);
    }

    public void cancel(Long memberId, Long reservationId) {
        Reservation reservation = reservationRepository
                .findByIdAndMemberId(reservationId, memberId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        GymBlock block = gymBlockRepository
                .findById(reservation.getGymBlockId())
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        int cancellationWindowHours = findGymOrThrow(block.getGymId()).getCancellationWindowHours();
        requireWithinBookingWindow(cancellationWindowHours, reservation.getClassDate(), block.getStartTime());
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> myReservations(Long memberId) {
        return reservationRepository.findByMemberIdAndStatusOrderByClassDateAsc(memberId, ReservationStatus.BOOKED)
                .stream()
                .map(r -> toResponse(
                        r, gymBlockRepository.findById(r.getGymBlockId()).orElse(null)))
                .toList();
    }

    private Gym findGymOrThrow(Long gymId) {
        return gymRepository.findById(gymId).orElseThrow(() -> new GymNotFoundException(gymId));
    }

    private boolean isWithinBookingWindow(int cancellationWindowHours, LocalDate classDate, LocalTime startTime) {
        ZonedDateTime classStart = ZonedDateTime.of(classDate, startTime, GYM_ZONE);
        return ZonedDateTime.now(GYM_ZONE).plusHours(cancellationWindowHours).isBefore(classStart);
    }

    private void requireWithinBookingWindow(int cancellationWindowHours, LocalDate classDate, LocalTime startTime) {
        if (!isWithinBookingWindow(cancellationWindowHours, classDate, startTime)) {
            throw new BookingWindowClosedException(
                    "Debes reservar o cancelar con al menos " + cancellationWindowHours + " horas de anticipación a la clase");
        }
    }

    private ReservationResponse toResponse(Reservation reservation, GymBlock block) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getGymBlockId(),
                block != null ? block.getLabel() : null,
                reservation.getClassDate(),
                block != null ? block.getStartTime() : null,
                block != null ? block.getEndTime() : null,
                reservation.getStatus(),
                reservation.getCreatedAt());
    }
}
