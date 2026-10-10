package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.GymPlan;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.GymBlockOccurrenceResponse;
import com.cortesdev.mygym.models.dto.MemberReservation;
import com.cortesdev.mygym.models.dto.OccurrenceAttendees;
import com.cortesdev.mygym.models.dto.OccurrenceSearchResult;
import com.cortesdev.mygym.models.dto.PageResponse;
import com.cortesdev.mygym.models.TvCheckinCode;
import com.cortesdev.mygym.models.dto.ReservationCreateRequest;
import com.cortesdev.mygym.models.dto.ReservationResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymPlanRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.repositories.TvCheckinCodeRepository;
import com.cortesdev.mygym.services.exception.AlreadyCheckedInException;
import com.cortesdev.mygym.services.exception.AttendeesHiddenException;
import com.cortesdev.mygym.services.exception.BookingWindowClosedException;
import com.cortesdev.mygym.services.exception.CapacityExceededException;
import com.cortesdev.mygym.services.exception.CheckinCodeNotFoundException;
import com.cortesdev.mygym.services.exception.GymBlockNotFoundException;
import com.cortesdev.mygym.services.exception.GymClosedException;
import com.cortesdev.mygym.services.exception.GymNotFoundException;
import com.cortesdev.mygym.services.exception.MemberNotFoundException;
import com.cortesdev.mygym.services.exception.MonthlyQuotaExceededException;
import com.cortesdev.mygym.services.exception.NoActiveReservationException;
import com.cortesdev.mygym.services.exception.ReservationNotFoundException;
import com.cortesdev.mygym.services.exception.SubscriptionRequiredException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ReservationService {

    private static final ZoneId GYM_ZONE = ZoneId.of("America/Santiago");
    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);
    private static final DateTimeFormatter EXHAUSTED_DATE_FORMAT =
            DateTimeFormatter.ofPattern("d 'de' MMMM", new Locale("es", "CL"));

    // Tope duro de página: aunque el cliente pida size=10000, nunca se devuelve más que esto.
    private static final int MAX_PAGE_SIZE = 50;

    // Buscador de reservas futuras por nombre: mínimo de letras y tope de socios cuyas reservas se
    // traen — un texto corto como "an" puede coincidir con cientos de socios en un gimnasio grande.
    private static final int MIN_SEARCH_LENGTH = 2;
    static final int MAX_SEARCH_MEMBERS = 25;

    // Cuánto antes de que empiece la clase ya se puede marcar asistencia — llegar unos minutos
    // antes a estirar/cambiarse es normal, no tiene sentido que el QR recién funcione al segundo
    // exacto de inicio.
    private static final Duration CHECKIN_EARLY_WINDOW = Duration.ofMinutes(10);

    // Gracia después de que termina — a veces el socio junta el QR de la TV recién cuando ya
    // está saliendo. Es aditivo con CHECKIN_EARLY_WINDOW: si además tiene otra reserva que ya
    // empezó dentro de SU propia ventana, un solo escaneo marca ambas (mismo criterio que dos
    // clases simultáneas, ver checkIn) — no hay forma de colarse en una clase que no reservó,
    // esto solo mira las reservas propias del socio.
    private static final Duration CHECKIN_LATE_WINDOW = Duration.ofMinutes(15);

    private final GymBlockRepository gymBlockRepository;
    private final ReservationRepository reservationRepository;
    private final GymRepository gymRepository;
    private final AppUserRepository appUserRepository;
    private final MemberService memberService;
    private final TvCheckinCodeRepository checkinCodeRepository;
    private final WaitlistService waitlistService;
    private final GymClosureService gymClosureService;
    private final GymPlanRepository gymPlanRepository;
    private final MemberLifecycleEmailService memberLifecycleEmailService;

    @Transactional(readOnly = true)
    public List<GymBlockOccurrenceResponse> listOccurrences(Long gymId, Long memberId, LocalDate from, LocalDate to) {
        int bookingWindowMinutes = findGymOrThrow(gymId).getBookingWindowMinutes();
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
        Set<String> myWaitlistedOccurrences = waitlistService.myWaitlistedOccurrences(memberId, from, to);
        GymClosureService.ClosureSchedule closures = gymClosureService.scheduleFor(gymId, from, to);
        Map<String, Integer> takenByOccurrence = new HashMap<>();
        Map<String, Long> myReservationIdByOccurrence = new HashMap<>();
        Map<String, Instant> myCheckedInAtByOccurrence = new HashMap<>();
        for (Reservation reservation : bookedInRange) {
            String key = occurrenceKey(reservation.getGymBlockId(), reservation.getClassDate());
            takenByOccurrence.merge(key, 1, Integer::sum);
            if (reservation.getMemberId().equals(memberId)) {
                myReservationIdByOccurrence.put(key, reservation.getId());
                myCheckedInAtByOccurrence.put(key, reservation.getCheckedInAt());
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
                boolean closed = closures.isClosed(block.getId(), occurrenceDate);
                boolean bookable = !closed
                        && taken < block.getCapacity()
                        && isWithinWindow(bookingWindowMinutes, occurrenceDate, block.getStartTime());
                boolean past = isPastOccurrence(occurrenceDate, block.getEndTime());
                Long myReservationId = myReservationIdByOccurrence.get(key);
                boolean waitlisted = myWaitlistedOccurrences.contains(key);
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
                        myReservationId,
                        waitlisted,
                        myCheckedInAtByOccurrence.get(key),
                        closed));
            }
        }
        result.sort(Comparator.comparing(GymBlockOccurrenceResponse::classDate)
                .thenComparing(GymBlockOccurrenceResponse::startTime));
        return result;
    }

    // Quiénes están reservados en una clase puntual — pedido explícito del
    // usuario (admin y socios). El filtrado de qué campos exponer (email
    // solo para admins, nunca para otros socios) queda a cargo del llamador
    // (controller), no de este método: acá se devuelve el AppUser completo,
    // emparejado con el id de SU reserva (necesario para poder cancelarla puntualmente).
    @Transactional(readOnly = true)
    public List<MemberReservation> getOccurrenceAttendees(Long gymId, Long gymBlockId, LocalDate classDate) {
        gymBlockRepository
                .findByIdAndGymId(gymBlockId, gymId)
                .orElseThrow(() -> new GymBlockNotFoundException(gymBlockId, gymId));
        List<Reservation> reservations = reservationRepository.findByGymBlockIdAndClassDateAndStatus(
                gymBlockId, classDate, ReservationStatus.BOOKED);
        Map<Long, AppUser> membersById = appUserRepository
                .findAllById(reservations.stream().map(Reservation::getMemberId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(AppUser::getId, member -> member));
        return reservations.stream()
                .map(r -> new MemberReservation(membersById.get(r.getMemberId()), r.getId(), r.getCheckedInAt()))
                .filter(mr -> mr.member() != null)
                .sorted(Comparator.comparing(mr -> mr.member().getName()))
                .toList();
    }

    // Vista del SOCIO (ReservationController y la demo): igual que getOccurrenceAttendees pero
    // respeta Gym.showAttendeesToMembers. El panel del admin y la TV NO pasan por acá — siempre
    // ven el roster completo.
    @Transactional(readOnly = true)
    public List<MemberReservation> getOccurrenceAttendeesForMember(Long gymId, Long gymBlockId, LocalDate classDate) {
        if (!findGymOrThrow(gymId).isShowAttendeesToMembers()) {
            throw new AttendeesHiddenException();
        }
        return getOccurrenceAttendees(gymId, gymBlockId, classDate);
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
        return groupByOccurrence(bookedInRange);
    }

    // Buscador de reservas futuras por nombre de socio — pedido explícito del usuario para no
    // tener que recorrer bloque por bloque/semana por semana buscando a alguien puntual (ver
    // SKILL.md). Solo reservas classDate >= hoy: el caso de uso real es urgencia con una clase
    // próxima, no gestionar historial (eso ya lo cubre Historial aparte).
    @Transactional(readOnly = true)
    public OccurrenceSearchResult searchUpcomingReservations(Long gymId, String query) {
        // Un texto de menos de 2 letras coincide con medio gimnasio — el frontend ya no lo manda,
        // acá se cierra el mismo hueco para quien le pegue directo a la API.
        if (query == null || query.trim().length() < MIN_SEARCH_LENGTH) {
            return new OccurrenceSearchResult(List.of(), false);
        }
        List<AppUser> matches = appUserRepository
                .findByGymIdAndRoleAndNameContainingIgnoreCase(gymId, Role.MEMBER, query.trim())
                .stream()
                .sorted(Comparator.comparing((AppUser u) -> u.getName() == null ? "" : u.getName().toLowerCase())
                        .thenComparing(AppUser::getId))
                .toList();
        if (matches.isEmpty()) {
            return new OccurrenceSearchResult(List.of(), false);
        }
        boolean truncated = matches.size() > MAX_SEARCH_MEMBERS;
        List<Long> memberIds = matches.stream().limit(MAX_SEARCH_MEMBERS).map(AppUser::getId).toList();
        List<Reservation> upcoming = reservationRepository.findByMemberIdInAndStatusAndClassDateGreaterThanEqual(
                memberIds, ReservationStatus.BOOKED, LocalDate.now(GYM_ZONE));
        return new OccurrenceSearchResult(groupByOccurrence(upcoming), truncated);
    }

    // Compartido por getOccurrenceAttendeesForRange (rango de fechas) y
    // searchUpcomingReservations (coincidencia de nombre) — mismo agrupamiento por
    // (gymBlockId, classDate), solo cambia de dónde sale la lista de reservas de entrada.
    private List<OccurrenceAttendees> groupByOccurrence(List<Reservation> reservations) {
        Map<Long, AppUser> membersById = appUserRepository
                .findAllById(reservations.stream().map(Reservation::getMemberId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(AppUser::getId, member -> member));

        Map<OccurrenceGroupKey, List<Reservation>> reservationsByOccurrence = new LinkedHashMap<>();
        for (Reservation reservation : reservations) {
            if (!membersById.containsKey(reservation.getMemberId())) {
                continue;
            }
            reservationsByOccurrence
                    .computeIfAbsent(
                            new OccurrenceGroupKey(reservation.getGymBlockId(), reservation.getClassDate()),
                            key -> new ArrayList<>())
                    .add(reservation);
        }

        return reservationsByOccurrence.entrySet().stream()
                .map(entry -> new OccurrenceAttendees(
                        entry.getKey().gymBlockId(),
                        entry.getKey().classDate(),
                        entry.getValue().stream()
                                .map(r -> new MemberReservation(membersById.get(r.getMemberId()), r.getId(), r.getCheckedInAt()))
                                .sorted(Comparator.comparing(mr -> mr.member().getName()))
                                .toList()))
                .toList();
    }

    private record OccurrenceGroupKey(Long gymBlockId, LocalDate classDate) {}

    public ReservationResponse book(Long gymId, Long memberId, ReservationCreateRequest request) {
        int bookingWindowMinutes = findGymOrThrow(gymId).getBookingWindowMinutes();
        GymBlock block = gymBlockRepository
                .findByIdAndGymId(request.gymBlockId(), gymId)
                .filter(GymBlock::isActive)
                .orElseThrow(() -> new GymBlockNotFoundException(gymId, request.gymBlockId()));

        if (request.classDate().getDayOfWeek() != block.getDayOfWeek()) {
            throw new BookingWindowClosedException("La fecha elegida no coincide con el día de la semana de este bloque");
        }
        if (!isWithinWindow(bookingWindowMinutes, request.classDate(), block.getStartTime())) {
            throw new BookingWindowClosedException("Las reservas para esta clase cerraron "
                    + formatMinutes(bookingWindowMinutes) + " antes del inicio");
        }

        String closedReason = gymClosureService.closedReasonFor(gymId, block.getId(), request.classDate());
        if (closedReason != null) {
            throw new GymClosedException(closedReason);
        }

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
        if (!memberService.hasQuotaAvailable(member)) {
            throw new MonthlyQuotaExceededException();
        }

        Reservation reservation = Reservation.builder()
                .gymBlockId(block.getId())
                .memberId(memberId)
                .classDate(request.classDate())
                .status(ReservationStatus.BOOKED)
                .build();
        ReservationResponse response = toResponse(reservationRepository.save(reservation), block);
        notifyIfClassesExhausted(gymId, member);
        // Si venía de la lista de espera (o simplemente estaba anotado y consiguió cupo por su
        // cuenta), ya no tiene sentido que siga esperando esta misma clase.
        waitlistService.clearOnBooked(block.getId(), request.classDate(), memberId);
        return response;
    }

    // Si la reserva recién guardada usó la ÚLTIMA clase del período, le llega un email "usaste todas
    // tus clases" (la app muestra su propio aviso al abrir /member). Best-effort: el envío nunca
    // tumba la reserva (MemberLifecycleEmailService.send ya traga sus errores; esto cubre el resto).
    void notifyIfClassesExhausted(Long gymId, AppUser member) {
        try {
            Integer remaining = memberService.remainingClasses(member);
            if (remaining == null || remaining > 0) {
                return;
            }
            GymPlan plan = gymPlanRepository.findById(member.getPlanId()).orElse(null);
            Gym gym = gymRepository.findById(gymId).orElse(null);
            if (plan == null || gym == null) {
                return;
            }
            String validUntil = EXHAUSTED_DATE_FORMAT.format(memberService.periodEnd(member).toLocalDate());
            memberLifecycleEmailService.sendClassesExhaustedMember(gym, member, plan, validUntil);
        } catch (RuntimeException e) {
            log.warn("No se pudo avisar clases agotadas al socio {}: {}", member.getId(), e.getMessage());
        }
    }

    public void cancel(Long memberId, Long reservationId) {
        Reservation reservation = reservationRepository
                .findByIdAndMemberId(reservationId, memberId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        GymBlock block = gymBlockRepository
                .findById(reservation.getGymBlockId())
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        int cancellationWindowMinutes = findGymOrThrow(block.getGymId()).getCancellationWindowMinutes();
        if (!isWithinWindow(cancellationWindowMinutes, reservation.getClassDate(), block.getStartTime())) {
            throw new BookingWindowClosedException("Solo puedes cancelar hasta "
                    + formatMinutes(cancellationWindowMinutes) + " antes del inicio");
        }
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);
        waitlistService.onSpotFreed(block.getId(), reservation.getClassDate());
    }

    // Vía de urgencia pedida explícitamente por el usuario: el socio llama al admin porque no
    // llega a cancelar solo (bloqueado por la ventana de cancelación) y no quiere que esa
    // clase le cuente como usada. A propósito NO revisa la ventana de cancelación — esa es
    // justo la ventana que el admin necesita poder saltarse. Sí valida que la reserva sea de
    // su propio gym (nunca filtrar que existe en otro) y que la clase no haya pasado ya
    // (cancelar algo que ya ocurrió no tiene sentido y reescribiría historial).
    public void cancelAsAdmin(Long gymId, Long reservationId) {
        Reservation reservation = reservationRepository
                .findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        GymBlock block = gymBlockRepository
                .findById(reservation.getGymBlockId())
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        if (!block.getGymId().equals(gymId)) {
            throw new ReservationNotFoundException(reservationId);
        }
        if (reservation.getClassDate().isBefore(LocalDate.now(GYM_ZONE))) {
            throw new BookingWindowClosedException("No puedes cancelar una clase que ya pasó");
        }
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);
        waitlistService.onSpotFreed(block.getId(), reservation.getClassDate());
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> myReservations(Long memberId) {
        // Solo desde hoy en adelante: las anteriores crecen sin límite y se piden paginadas
        // (myPastReservations). Las de hoy que ya terminaron siguen viniendo acá — el frontend las
        // clasifica como pasadas según la hora real de término.
        return toResponses(reservationRepository.findByMemberIdAndStatusAndClassDateGreaterThanEqualOrderByClassDateAsc(
                memberId, ReservationStatus.BOOKED, LocalDate.now(GYM_ZONE)));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> myPastReservations(Long memberId, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "classDate").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<Reservation> result = reservationRepository.findByMemberIdAndStatusAndClassDateLessThan(
                memberId, ReservationStatus.BOOKED, LocalDate.now(GYM_ZONE), pageable);
        List<ReservationResponse> items = toResponses(result.getContent());
        return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements(), result.hasNext());
    }

    private List<ReservationResponse> toResponses(List<Reservation> reservations) {
        // Mismo N+1 que tenía listOccurrences: antes se buscaba el GymBlock de
        // cada reserva con una query aparte — una sola consulta con los ids
        // únicos evita que eso se vuelva un problema de escala.
        List<Long> blockIds = reservations.stream().map(Reservation::getGymBlockId).distinct().toList();
        Map<Long, GymBlock> blocksById = blockIds.isEmpty()
                ? Map.of()
                : gymBlockRepository.findAllById(blockIds).stream()
                        .collect(Collectors.toMap(GymBlock::getId, b -> b));
        return reservations.stream()
                .map(r -> toResponse(r, blocksById.get(r.getGymBlockId())))
                .toList();
    }

    /**
     * Canjea el QR rotativo que la TV muestra durante una clase en curso (ver TvScreenService.
     * createCheckinCode) — confirma asistencia real, distinta de haber reservado. No exige que el
     * código identifique una clase puntual: resuelve el gym del código, y de ahí busca ENTRE LAS
     * RESERVAS DE HOY DEL SOCIO cuál cae dentro de su ventana horaria real ahora mismo. Casi
     * siempre da una sola coincidencia; si da más de una (dos reservas simultáneas, caso raro)
     * marca todas — la asistencia es aditiva, nunca exclusiva.
     */
    public List<String> checkIn(Long memberId, String code) {
        TvCheckinCode checkin = checkinCodeRepository
                .findByCode(code)
                .filter(c -> c.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new CheckinCodeNotFoundException(code));

        // Si el socio no pertenece a ESE gimnasio, tratarlo igual que "no tengo nada que marcar"
        // acá — nunca revelar que el código es válido para un gym distinto al suyo.
        if (appUserRepository.findByIdAndGymId(memberId, checkin.getGymId()).isEmpty()) {
            throw new NoActiveReservationException();
        }

        LocalDate today = LocalDate.now(GYM_ZONE);
        List<Reservation> todaysBooked =
                reservationRepository.findByMemberIdAndClassDateAndStatus(memberId, today, ReservationStatus.BOOKED);
        List<Long> blockIds = todaysBooked.stream().map(Reservation::getGymBlockId).distinct().toList();
        Map<Long, GymBlock> blocksById = blockIds.isEmpty()
                ? Map.of()
                : gymBlockRepository.findAllById(blockIds).stream()
                        .collect(Collectors.toMap(GymBlock::getId, b -> b));

        ZonedDateTime nowZoned = ZonedDateTime.now(GYM_ZONE);
        List<Reservation> windowMatches = todaysBooked.stream()
                .filter(r -> {
                    GymBlock block = blocksById.get(r.getGymBlockId());
                    if (block == null || !block.getGymId().equals(checkin.getGymId())) {
                        return false;
                    }
                    ZonedDateTime start = ZonedDateTime.of(today, block.getStartTime(), GYM_ZONE).minus(CHECKIN_EARLY_WINDOW);
                    ZonedDateTime end = ZonedDateTime.of(today, block.getEndTime(), GYM_ZONE).plus(CHECKIN_LATE_WINDOW);
                    return !nowZoned.isBefore(start) && !nowZoned.isAfter(end);
                })
                .toList();

        // Si la única razón de no encontrar nada es que ya había marcado asistencia antes, avisar
        // eso en vez del mensaje genérico de "no tienes reserva" — es un caso distinto y confunde.
        if (windowMatches.stream().allMatch(r -> r.getCheckedInAt() != null)) {
            if (windowMatches.isEmpty()) {
                throw new NoActiveReservationException();
            }
            throw new AlreadyCheckedInException();
        }

        List<Reservation> matches =
                windowMatches.stream().filter(r -> r.getCheckedInAt() == null).toList();

        Instant now = Instant.now();
        List<String> classLabels = new ArrayList<>();
        for (Reservation reservation : matches) {
            reservation.setCheckedInAt(now);
            reservationRepository.save(reservation);
            GymBlock block = blocksById.get(reservation.getGymBlockId());
            classLabels.add(block != null ? block.getLabel() : "tu clase");
        }
        return classLabels;
    }

    private String occurrenceKey(Long gymBlockId, LocalDate classDate) {
        return gymBlockId + "|" + classDate;
    }

    private Gym findGymOrThrow(Long gymId) {
        return gymRepository.findById(gymId).orElseThrow(() -> new GymNotFoundException(gymId));
    }

    /** true si faltan MÁS de `windowMinutes` minutos para el inicio — sirve tanto para el límite de
     *  reserva como para el de cancelación (son dos datos distintos del gym, ver V36). */
    private boolean isWithinWindow(int windowMinutes, LocalDate classDate, LocalTime startTime) {
        ZonedDateTime classStart = ZonedDateTime.of(classDate, startTime, GYM_ZONE);
        return ZonedDateTime.now(GYM_ZONE).plusMinutes(windowMinutes).isBefore(classStart);
    }

    /** "2 h", "1 h 30 min", "45 min" — mismo formato que usa el frontend en los avisos al socio. */
    static String formatMinutes(int minutes) {
        int hours = minutes / 60;
        int rest = minutes % 60;
        if (hours == 0) {
            return rest + " min";
        }
        return rest == 0 ? hours + " h" : hours + " h " + rest + " min";
    }

    /** Una ocurrencia queda "pasada" recién cuando termina, no cuando empieza — mientras la clase está en curso no es "pasada". */
    private boolean isPastOccurrence(LocalDate classDate, LocalTime endTime) {
        ZonedDateTime classEnd = ZonedDateTime.of(classDate, endTime, GYM_ZONE);
        return classEnd.isBefore(ZonedDateTime.now(GYM_ZONE));
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
                reservation.getCreatedAt(),
                reservation.getCheckedInAt());
    }
}
