package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.dto.GymBlockOccurrenceResponse;
import com.cortesdev.mygym.models.dto.OccurrenceAttendees;
import com.cortesdev.mygym.models.dto.ReservationCreateRequest;
import com.cortesdev.mygym.models.dto.ReservationResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.services.exception.BookingWindowClosedException;
import com.cortesdev.mygym.services.exception.CapacityExceededException;
import com.cortesdev.mygym.services.exception.GymBlockNotFoundException;
import com.cortesdev.mygym.services.exception.GymNotFoundException;
import com.cortesdev.mygym.services.exception.MemberNotFoundException;
import com.cortesdev.mygym.services.exception.ReservationNotFoundException;
import com.cortesdev.mygym.services.exception.SubscriptionRequiredException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
    private final AppUserRepository appUserRepository;
    private final MemberService memberService;

    @Transactional(readOnly = true)
    public List<GymBlockOccurrenceResponse> listOccurrences(Long gymId, Long memberId, LocalDate from, LocalDate to) {
        int cancellationWindowHours = findGymOrThrow(gymId).getCancellationWindowHours();
        List<GymBlock> blocks =
                gymBlockRepository.findByGymId(gymId).stream().filter(GymBlock::isActive).toList();
        List<Long> blockIds = blocks.stream().map(GymBlock::getId).toList();

        // Antes esto era 1 query por cada combinación bloque×día del rango pedido
        // (countByGymBlockIdAndClassDateAndStatus dentro del loop) — con un mes
        // completo y un gym con muchos bloques (ej. Fortis, ~30 bloques activos)
        // eran cientos de round-trips secuenciales a Supabase, suficiente para
        // que la carga nunca terminara en la práctica. Se trae una sola vez
        // todo lo reservado en el rango y se cuenta/busca en memoria — mismo
        // patrón que ya se usaba para "mis reservas", pero reutilizado también
        // para el conteo de cupo.
        List<Reservation> bookedInRange = blockIds.isEmpty()
                ? List.of()
                : reservationRepository.findByGymBlockIdInAndClassDateBetweenAndStatus(
                        blockIds, from, to, ReservationStatus.BOOKED);
        Map<String, Integer> takenByOccurrence = new HashMap<>();
        Map<String, Long> myReservationIdByOccurrence = new HashMap<>();
        for (Reservation reservation : bookedInRange) {
            String key = occurrenceKey(reservation.getGymBlockId(), reservation.getClassDate());
            takenByOccurrence.merge(key, 1, Integer::sum);
            if (reservation.getMemberId().equals(memberId)) {
                myReservationIdByOccurrence.put(key, reservation.getId());
            }
        }

        List<GymBlockOccurrenceResponse> result = new ArrayList<>();
        for (GymBlock block : blocks) {
            for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                if (date.getDayOfWeek() != block.getDayOfWeek()) {
                    continue;
                }
                LocalDate occurrenceDate = date;
                String key = occurrenceKey(block.getId(), occurrenceDate);
                int taken = takenByOccurrence.getOrDefault(key, 0);
                boolean bookable = taken < block.getCapacity()
                        && isWithinBookingWindow(cancellationWindowHours, occurrenceDate, block.getStartTime());
                boolean past = isPastOccurrence(occurrenceDate, block.getEndTime());
                Long myReservationId = myReservationIdByOccurrence.get(key);
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
                        past,
                        myReservationId));
            }
        }
        result.sort(Comparator.comparing(GymBlockOccurrenceResponse::classDate)
                .thenComparing(GymBlockOccurrenceResponse::startTime));
        return result;
    }

    // Quiénes están reservados en una clase puntual — pedido explícito del
    // usuario (admin y socios). El filtrado de qué campos exponer (email
    // solo para admins, nunca para otros socios) queda a cargo del llamador
    // (controller), no de este método: acá se devuelve el AppUser completo.
    @Transactional(readOnly = true)
    public List<AppUser> getOccurrenceAttendees(Long gymId, Long gymBlockId, LocalDate classDate) {
        gymBlockRepository
                .findByIdAndGymId(gymBlockId, gymId)
                .orElseThrow(() -> new GymBlockNotFoundException(gymBlockId, gymId));
        List<Long> memberIds = reservationRepository
                .findByGymBlockIdAndClassDateAndStatus(gymBlockId, classDate, ReservationStatus.BOOKED)
                .stream()
                .map(Reservation::getMemberId)
                .toList();
        return appUserRepository.findAllById(memberIds).stream()
                .sorted(Comparator.comparing(AppUser::getName))
                .toList();
    }

    // Batch equivalente a llamar getOccurrenceAttendees() una vez por cada bloque×día de la
    // semana — que es justo lo que hacía la pestaña Historial del frontend antes: un fan-out
    // de N requests en paralelo (N = bloques semanales del gym), cada una abriendo su propia
    // transacción contra un pool de solo 5 conexiones a la Supabase remota. Con pocos bloques
    // ya alcanzaba para que la mayoría de las requests quedara haciendo cola esperando una
    // conexión libre. Mismo patrón que listOccurrences: una sola query de reservas en el rango
    // + un solo findAllById, todo agrupado en memoria — un solo round-trip en vez de N.
    // Solo devuelve ocurrencias con al menos 1 asistente (las vacías no le sirven a Historial).
    @Transactional(readOnly = true)
    public List<OccurrenceAttendees> getOccurrenceAttendeesForRange(Long gymId, LocalDate from, LocalDate to) {
        List<GymBlock> blocks =
                gymBlockRepository.findByGymId(gymId).stream().filter(GymBlock::isActive).toList();
        List<Long> blockIds = blocks.stream().map(GymBlock::getId).toList();
        List<Reservation> bookedInRange = blockIds.isEmpty()
                ? List.of()
                : reservationRepository.findByGymBlockIdInAndClassDateBetweenAndStatus(
                        blockIds, from, to, ReservationStatus.BOOKED);

        Map<Long, AppUser> membersById = appUserRepository
                .findAllById(bookedInRange.stream().map(Reservation::getMemberId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(AppUser::getId, member -> member));

        Map<OccurrenceGroupKey, List<AppUser>> attendeesByOccurrence = new LinkedHashMap<>();
        for (Reservation reservation : bookedInRange) {
            AppUser member = membersById.get(reservation.getMemberId());
            if (member == null) {
                continue;
            }
            attendeesByOccurrence
                    .computeIfAbsent(
                            new OccurrenceGroupKey(reservation.getGymBlockId(), reservation.getClassDate()),
                            key -> new ArrayList<>())
                    .add(member);
        }

        return attendeesByOccurrence.entrySet().stream()
                .map(entry -> new OccurrenceAttendees(
                        entry.getKey().gymBlockId(),
                        entry.getKey().classDate(),
                        entry.getValue().stream().sorted(Comparator.comparing(AppUser::getName)).toList()))
                .toList();
    }

    private record OccurrenceGroupKey(Long gymBlockId, LocalDate classDate) {}

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

        // Gate real de suscripción — hasta esta validación, cualquier socio con rol MEMBER
        // podía reservar gratis sin importar si había pagado o no (el estado Activo/Vencido
        // era puramente decorativo en la UI). Ver MemberService.hasActiveMembership.
        AppUser member = appUserRepository
                .findByIdAndGymId(memberId, gymId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
        if (!memberService.hasActiveMembership(member)) {
            throw new SubscriptionRequiredException("Necesitas un plan activo para reservar clases");
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
        List<Reservation> reservations =
                reservationRepository.findByMemberIdAndStatusOrderByClassDateAsc(memberId, ReservationStatus.BOOKED);
        // Mismo N+1 que tenía listOccurrences: antes se buscaba el GymBlock de
        // cada reserva con una query aparte. Sin fecha de corte en la query de
        // arriba, este historial crece sin límite mientras el socio use la
        // app (no hay archivado de reservas pasadas) — una sola consulta con
        // los ids únicos evita que eso se vuelva un problema de escala.
        List<Long> blockIds = reservations.stream().map(Reservation::getGymBlockId).distinct().toList();
        Map<Long, GymBlock> blocksById = blockIds.isEmpty()
                ? Map.of()
                : gymBlockRepository.findAllById(blockIds).stream()
                        .collect(Collectors.toMap(GymBlock::getId, b -> b));
        return reservations.stream()
                .map(r -> toResponse(r, blocksById.get(r.getGymBlockId())))
                .toList();
    }

    private String occurrenceKey(Long gymBlockId, LocalDate classDate) {
        return gymBlockId + "|" + classDate;
    }

    private Gym findGymOrThrow(Long gymId) {
        return gymRepository.findById(gymId).orElseThrow(() -> new GymNotFoundException(gymId));
    }

    private boolean isWithinBookingWindow(int cancellationWindowHours, LocalDate classDate, LocalTime startTime) {
        ZonedDateTime classStart = ZonedDateTime.of(classDate, startTime, GYM_ZONE);
        return ZonedDateTime.now(GYM_ZONE).plusHours(cancellationWindowHours).isBefore(classStart);
    }

    /** Una ocurrencia queda "pasada" recién cuando termina, no cuando empieza — mientras la clase está en curso no es "pasada". */
    private boolean isPastOccurrence(LocalDate classDate, LocalTime endTime) {
        ZonedDateTime classEnd = ZonedDateTime.of(classDate, endTime, GYM_ZONE);
        return classEnd.isBefore(ZonedDateTime.now(GYM_ZONE));
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
