package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.CheckinMethod;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.GymPlan;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.AgendaOccurrence;
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
import com.cortesdev.mygym.services.exception.InvalidAttendanceException;
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
import com.cortesdev.mygym.services.exception.ProfileIncompleteException;
import com.cortesdev.mygym.services.exception.ReservationNotFoundException;
import com.cortesdev.mygym.services.exception.SubscriptionRequiredException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    // Hasta cuántos días después de terminada la clase se puede corregir su asistencia a mano.
    static final int ATTENDANCE_EDIT_DAYS = 7;

    // Tope del rango que puede pedir la Agenda del admin (la UI pide de a 7 días).
    static final int MAX_AGENDA_DAYS = 31;
    static final int MAX_HISTORY_RANGE_DAYS = 93;
    // Calendario del socio: el período pagado dura siempre 1 mes (≈31 días) y, sin pago, se muestra el mes calendario (≤31);
    // 62 deja margen de sobra para pedir dos meses seguidos sin abrir la puerta a rangos absurdos.
    static final int MAX_OCCURRENCES_RANGE_DAYS = 62;

    private final GymBlockRepository gymBlockRepository;
    private final ReservationRepository reservationRepository;
    private final GymRepository gymRepository;
    private final AppUserRepository appUserRepository;
    private final MemberService memberService;
    private final TvCheckinCodeRepository checkinCodeRepository;
    private final WaitlistService waitlistService;
    private final PushNotificationService pushService;
    private final StaffCancellationPush staffCancellationPush;
    private final MemberChangeNotifier memberChangeNotifier;
    private final GymClosureService gymClosureService;
    private final GymPlanRepository gymPlanRepository;
    private final MemberLifecycleEmailService memberLifecycleEmailService;
    private final FixedWeekRule fixedWeekRule;

    @Transactional(readOnly = true)
    public List<GymBlockOccurrenceResponse> listOccurrences(Long gymId, Long memberId, LocalDate from, LocalDate to) {
        // Sin tope, from=0001-01-01&to=9999-12-31 hacía un bucle de millones de días por bloque y dejaba sin memoria a la
        // única instancia (la puede pedir cualquier socio). El calendario real pide el mes o el período pagado.
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_OCCURRENCES_RANGE_DAYS) {
            throw new InvalidAttendanceException("El rango del calendario debe ser de 1 a " + MAX_OCCURRENCES_RANGE_DAYS + " días");
        }
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
        Set<String> myFixedOccurrences = new java.util.HashSet<>();
        for (Reservation reservation : bookedInRange) {
            String key = occurrenceKey(reservation.getGymBlockId(), reservation.getClassDate());
            takenByOccurrence.merge(key, 1, Integer::sum);
            if (reservation.getMemberId().equals(memberId)) {
                myReservationIdByOccurrence.put(key, reservation.getId());
                myCheckedInAtByOccurrence.put(key, reservation.getCheckedInAt());
                if (reservation.getFixedClassId() != null) {
                    myFixedOccurrences.add(key);
                }
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
                        closed,
                        myFixedOccurrences.contains(key)));
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
                .map(r -> new MemberReservation(membersById.get(r.getMemberId()), r.getId(), r.getCheckedInAt(), r.getCheckinMethod()))
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
        // Sin tope, from=2000-01-01&to=2100-01-01 cargaba en memoria todas las reservas del gimnasio.
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_HISTORY_RANGE_DAYS) {
            throw new InvalidAttendanceException("El rango debe ser de 1 a " + MAX_HISTORY_RANGE_DAYS + " días");
        }
        List<GymBlock> blocks =
                gymBlockRepository.findByGymId(gymId).stream().filter(GymBlock::isActive).toList();
        List<Long> blockIds = blocks.stream().map(GymBlock::getId).toList();
        List<Reservation> bookedInRange = blockIds.isEmpty()
                ? List.of()
                : reservationRepository.findByGymBlockIdInAndClassDateBetweenAndStatus(
                        blockIds, from, to, ReservationStatus.BOOKED);
        return groupByOccurrence(bookedInRange);
    }

    // Agenda del admin (pestaña Clases): UNA entrada por cada ocurrencia (bloque × fecha que coincide con su
    // día de la semana) con cupos, cierre de emergencia, lista de espera y asistentes — incluidas las clases
    // sin reservas. Todo en lote (una query de reservas, una de cierres, una de espera, un findAllById):
    // el pool de Hikari es de solo 5 conexiones, ver getOccurrenceAttendeesForRange. Incluye los bloques
    // activos y los inactivos que igual tuvieron reservas ese día (para no perder historial).
    @Transactional(readOnly = true)
    public List<AgendaOccurrence> getAgenda(Long gymId, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_AGENDA_DAYS) {
            throw new InvalidAttendanceException("El rango de la agenda debe ser de 1 a " + MAX_AGENDA_DAYS + " días");
        }
        List<GymBlock> blocks = gymBlockRepository.findByGymId(gymId);
        if (blocks.isEmpty()) {
            return List.of();
        }
        List<Long> blockIds = blocks.stream().map(GymBlock::getId).toList();
        Map<String, OccurrenceAttendees> attendeesByKey = new HashMap<>();
        for (OccurrenceAttendees occurrence : groupByOccurrence(reservationRepository
                .findByGymBlockIdInAndClassDateBetweenAndStatus(blockIds, from, to, ReservationStatus.BOOKED))) {
            attendeesByKey.put(occurrenceKey(occurrence.gymBlockId(), occurrence.classDate()), occurrence);
        }
        GymClosureService.ClosureSchedule closures = gymClosureService.scheduleFor(gymId, from, to);
        Map<String, Integer> waiting = waitlistService.countWaitingByOccurrence(blockIds, from, to);

        List<AgendaOccurrence> result = new ArrayList<>();
        Map<Long, LocalTime> startByBlock = new HashMap<>();
        for (GymBlock block : blocks) {
            startByBlock.put(block.getId(), block.getStartTime());
            for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                if (date.getDayOfWeek() != block.getDayOfWeek()) {
                    continue;
                }
                String key = occurrenceKey(block.getId(), date);
                OccurrenceAttendees occurrence = attendeesByKey.get(key);
                List<MemberReservation> attendees = occurrence == null ? List.of() : occurrence.attendees();
                if (!block.isActive() && attendees.isEmpty()) {
                    continue;
                }
                boolean closed = closures.isClosed(block.getId(), date);
                result.add(new AgendaOccurrence(
                        block.getId(),
                        date,
                        block.getCapacity(),
                        attendees.size(),
                        closed,
                        closed ? closures.reasonFor(block.getId(), date) : null,
                        waiting.getOrDefault(key, 0),
                        attendees));
            }
        }
        result.sort(Comparator.comparing(AgendaOccurrence::classDate)
                .thenComparing(o -> startByBlock.get(o.gymBlockId())));
        return result;
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
                                .map(r -> new MemberReservation(membersById.get(r.getMemberId()), r.getId(), r.getCheckedInAt(), r.getCheckinMethod()))
                                .sorted(Comparator.comparing(mr -> mr.member().getName()))
                                .toList()))
                .toList();
    }

    private record OccurrenceGroupKey(Long gymBlockId, LocalDate classDate) {}

    public ReservationResponse book(Long gymId, Long memberId, ReservationCreateRequest request) {
        Gym gym = findGymOrThrow(gymId);
        int bookingWindowMinutes = gym.getBookingWindowMinutes();
        // Se bloquea la fila del bloque hasta el commit: dos reservas simultáneas de la misma clase se ponen en fila y
        // la segunda ve el cupo ya ocupado (antes ambas contaban "queda 1" y las dos entraban, pasando la capacidad).
        GymBlock block = gymBlockRepository
                .findLockedByIdAndGymId(request.gymBlockId(), gymId)
                .filter(GymBlock::isActive)
                .orElseThrow(() -> new GymBlockNotFoundException(gymId, request.gymBlockId()));

        if (request.classDate().getDayOfWeek() != block.getDayOfWeek()) {
            throw new BookingWindowClosedException("La fecha elegida no coincide con el día de la semana de este bloque");
        }
        if (!isWithinWindow(bookingWindowMinutes, request.classDate(), block.getStartTime())) {
            throw new BookingWindowClosedException("Las reservas para esta clase cerraron "
                    + formatMinutes(bookingWindowMinutes) + " antes del inicio");
        }
        fixedWeekRule.assertAllowed(gym, memberId, request.classDate(), "reservar");

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
        // También se bloquea la fila del socio: sus reservas simultáneas a clases DISTINTAS se ponen en fila y la cuota
        // mensual se cuenta de verdad (sin esto, N llamadas en paralelo pasaban el tope de su plan).
        AppUser member = appUserRepository
                .findLockedByIdAndGymId(memberId, gymId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
        if (!memberService.hasActiveMembership(member)) {
            throw new SubscriptionRequiredException("Necesitas un plan activo para reservar clases");
        }
        if (!memberService.hasQuotaAvailable(member)) {
            throw new MonthlyQuotaExceededException();
        }
        // El cupo mensual solo cuenta las clases del período pagado: sin este límite, una fecha posterior
        // al vencimiento no gastaba cupo y quedaba reservada aunque el plan ya hubiera vencido.
        if (!MemberService.isWithinPaidPeriod(member, request.classDate())) {
            throw new BookingWindowClosedException(paidPeriodMessage(member));
        }
        // RUT, dirección, fecha de nacimiento y celular son obligatorios (pantalla de datos de /member);
        // el servidor lo hace cumplir igual para quien llame a la API directo.
        if (!member.isProfileComplete()) {
            throw new ProfileIncompleteException();
        }

        ReservationResponse response = toResponse(createBooked(block.getId(), memberId, request.classDate(), null), block);
        // Después del commit: el aviso manda un correo (HTTP a Resend) y, dentro de la transacción, mantendría
        // bloqueadas las filas del bloque y del socio mientras tanto, poniendo en fila a todas las reservas de esa clase.
        afterCommit(() -> notifyIfClassesExhausted(gymId, member));
        // Si venía de la lista de espera (o simplemente estaba anotado y consiguió cupo por su
        // cuenta), ya no tiene sentido que siga esperando esta misma clase.
        waitlistService.clearOnBooked(block.getId(), request.classDate(), memberId);
        return response;
    }

    /** Ejecuta la tarea cuando la transacción en curso se confirma; sin transacción (pruebas unitarias) la corre al tiro. */
    private static void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    /** Texto para un socio que intenta reservar (o anotarse a la espera) después de que vence su plan. */
    static String paidPeriodMessage(AppUser member) {
        LocalDate until = MemberService.paidUntil(member);
        return String.format(
                "Tu plan cubre hasta el %02d/%02d. Renuévalo para reservar clases de fechas posteriores",
                until.getDayOfMonth(), until.getMonthValue());
    }

    /** Crea y guarda la reserva BOOKED — sin validar nada (el llamador ya validó lo que le toca):
     *  la usa book() y la generación de clases fijas (que NO valida capacidad ni ventana). */
    Reservation createBooked(Long gymBlockId, Long memberId, LocalDate classDate, Long fixedClassId) {
        return reservationRepository.save(Reservation.builder()
                .gymBlockId(gymBlockId)
                .memberId(memberId)
                .classDate(classDate)
                .status(ReservationStatus.BOOKED)
                .fixedClassId(fixedClassId)
                .build());
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
            pushService.notifyUsers(List.of(member.getId()), PushMessages.classesExhausted(validUntil));
        } catch (RuntimeException e) {
            log.warn("No se pudo avisar clases agotadas al socio {}: {}", member.getId(), e.getMessage());
        }
    }

    public void cancel(Long memberId, Long reservationId) {
        Reservation reservation = reservationRepository
                .findByIdAndMemberId(reservationId, memberId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        // Cancelar algo ya cancelado (doble toque, o repetir la llamada a mano) no hace nada: sin esto cada repetición
        // volvía a avisar "se liberó un cupo" a la lista de espera y mandaba otro push al equipo del gimnasio.
        if (reservation.getStatus() != ReservationStatus.BOOKED) {
            return;
        }
        GymBlock block = gymBlockRepository
                .findById(reservation.getGymBlockId())
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        Gym gym = findGymOrThrow(block.getGymId());
        int cancellationWindowMinutes = gym.getCancellationWindowMinutes();
        if (!isWithinWindow(cancellationWindowMinutes, reservation.getClassDate(), block.getStartTime())) {
            throw new BookingWindowClosedException("Solo puedes cancelar hasta "
                    + formatMinutes(cancellationWindowMinutes) + " antes del inicio");
        }
        fixedWeekRule.assertAllowed(gym, memberId, reservation.getClassDate(), "cancelar");
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);
        waitlistService.onSpotFreed(block.getId(), reservation.getClassDate());
        // Aviso push al admin y profesores del gimnasio: solo cuando el SOCIO cancela (no cancelAsAdmin ni un cierre).
        // Best-effort: nunca debe tumbar la cancelacion.
        try {
            appUserRepository
                    .findById(memberId)
                    .ifPresent(member -> staffCancellationPush.memberCancelled(block, reservation.getClassDate(), member));
        } catch (RuntimeException e) {
            log.warn("No se pudo avisar la cancelacion al equipo del gimnasio {}: {}", block.getGymId(), e.getMessage());
        }
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
        if (reservation.getStatus() != ReservationStatus.BOOKED) {
            return; // ya estaba cancelada: nada que liberar ni avisar (ver cancel)
        }
        if (reservation.getClassDate().isBefore(LocalDate.now(GYM_ZONE))) {
            throw new BookingWindowClosedException("No puedes cancelar una clase que ya pasó");
        }
        boolean wasBooked = true;
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);
        waitlistService.onSpotFreed(block.getId(), reservation.getClassDate());
        // Antes esta cancelacion era silenciosa: el socio se enteraba al abrir la app.
        if (wasBooked) {
            memberChangeNotifier.reservationCancelledByStaff(findGymOrThrow(block.getGymId()), block, reservation);
        }
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
            reservation.setCheckinMethod(CheckinMethod.QR);
            reservationRepository.save(reservation);
            GymBlock block = blocksById.get(reservation.getGymBlockId());
            classLabels.add(block != null ? block.getLabel() : "tu clase");
        }
        return classLabels;
    }

    /**
     * Marca o desmarca a mano la asistencia (admin, profesor o super-admin). Solo desde que se abre la
     * ventana del QR hasta ATTENDANCE_EDIT_DAYS después de terminar la clase: así el historial no se
     * reescribe meses después. El cupo del plan no cambia (ya se descontó al reservar). Idempotente:
     * marcar dos veces no cambia la hora original. Una reserva de otro gym responde 404.
     */
    public AttendeeMark setAttendance(Long gymId, Long reservationId, boolean attended, Long actorUserId) {
        Reservation reservation = reservationRepository
                .findById(reservationId)
                .filter(r -> r.getStatus() == ReservationStatus.BOOKED)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        GymBlock block = gymBlockRepository
                .findById(reservation.getGymBlockId())
                .filter(b -> b.getGymId().equals(gymId))
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        if (!isAttendanceEditable(reservation.getClassDate(), block.getStartTime(), block.getEndTime())) {
            throw new InvalidAttendanceException(
                    "Solo puedes marcar asistencia desde que empieza la clase y hasta " + ATTENDANCE_EDIT_DAYS + " días después.");
        }
        if (attended) {
            if (reservation.getCheckedInAt() == null) {
                reservation.setCheckedInAt(Instant.now());
                reservation.setCheckinMethod(CheckinMethod.MANUAL);
                reservation.setCheckedInByUserId(actorUserId);
                reservationRepository.save(reservation);
            }
        } else if (reservation.getCheckedInAt() != null) {
            reservation.setCheckedInAt(null);
            reservation.setCheckinMethod(null);
            reservation.setCheckedInByUserId(actorUserId);
            reservationRepository.save(reservation);
        }
        return new AttendeeMark(reservation.getId(), reservation.getCheckedInAt(), reservation.getCheckinMethod());
    }

    /** Ventana de marcado a mano: desde 10 min antes del inicio hasta ATTENDANCE_EDIT_DAYS días después del fin. */
    static boolean isAttendanceEditable(LocalDate classDate, LocalTime startTime, LocalTime endTime) {
        ZonedDateTime now = ZonedDateTime.now(GYM_ZONE);
        ZonedDateTime opens = ZonedDateTime.of(classDate, startTime, GYM_ZONE).minus(CHECKIN_EARLY_WINDOW);
        ZonedDateTime closes = ZonedDateTime.of(classDate, endTime, GYM_ZONE).plusDays(ATTENDANCE_EDIT_DAYS);
        return !now.isBefore(opens) && !now.isAfter(closes);
    }

    /** Resultado de marcar a mano: lo que el frontend necesita para actualizar su fila. */
    public record AttendeeMark(Long reservationId, Instant checkedInAt, CheckinMethod checkinMethod) {}

    private String occurrenceKey(Long gymBlockId, LocalDate classDate) {
        return gymBlockId + "|" + classDate;
    }

    private Gym findGymOrThrow(Long gymId) {
        return gymRepository.findSettingsViewById(gymId).orElseThrow(() -> new GymNotFoundException(gymId));
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
                reservation.getCheckedInAt(),
                reservation.getFixedClassId() != null);
    }
}
