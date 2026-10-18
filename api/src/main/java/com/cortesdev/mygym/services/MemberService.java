package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.GymPlan;
import com.cortesdev.mygym.models.MemberFixedClass;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.MemberCreateRequest;
import com.cortesdev.mygym.models.dto.MemberImportRow;
import com.cortesdev.mygym.models.dto.MemberImportRowResult;
import com.cortesdev.mygym.models.dto.MemberResponse;
import com.cortesdev.mygym.models.dto.MemberSummaryResponse;
import com.cortesdev.mygym.models.dto.PageResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymPlanRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.MemberFixedClassRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.services.exception.DuplicateMemberEmailException;
import com.cortesdev.mygym.services.exception.GymNotFoundException;
import com.cortesdev.mygym.services.exception.MemberNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class MemberService {

    // Mismo huso y misma regla de "1 mes calendario desde el pago" que
    // usaba member.ts client-side (membershipPeriodEnd) — ahora calculada acá
    // porque paidAt ya se persiste de verdad (ver GymService.simulatePlanPayment).
    private static final ZoneId GYM_ZONE = ZoneId.of("America/Santiago");

    private static final Logger log = LoggerFactory.getLogger(MemberService.class);

    // Tope defensivo por request — el frontend ya manda el Excel en bloques de ~20 filas (ver
    // ImportMembersModal), esto es solo una red de seguridad contra un bug del cliente.
    private static final int MAX_IMPORT_ROWS = 100;

    private final AppUserRepository appUserRepository;
    private final GymPlanRepository gymPlanRepository;
    private final ReservationRepository reservationRepository;
    private final GymRepository gymRepository;
    private final MemberLifecycleEmailService memberLifecycleEmailService;
    private final MemberImportRowService memberImportRowService;
    private final PasswordAuthService passwordAuthService;
    private final MemberFixedClassRepository memberFixedClassRepository;
    private final GymBlockRepository gymBlockRepository;
    private final GymCapacityService gymCapacityService;

    public MemberResponse createMember(Long gymId, MemberCreateRequest request) {
        if (appUserRepository.existsByEmail(AppUser.normalizeEmail(request.email()))) {
            throw new DuplicateMemberEmailException(request.email());
        }
        // Cupo de socios del gimnasio (Gym.maxUsers): sin esto el limite se guardaba pero nadie lo aplicaba.
        gymCapacityService.assertMemberCapacity(
                gymRepository.findById(gymId).orElseThrow(() -> new GymNotFoundException(gymId)), 1);
        AppUser member = appUserRepository.save(AppUser.builder()
                .name(request.name())
                .email(request.email())
                .role(Role.MEMBER)
                .gymId(gymId)
                .active(true)
                .invitedAt(Instant.now())
                .build());

        // Alta manual por el admin — a diferencia de /j/{slug} (alta pública
        // con Google), acá el socio no recibía ningún email hasta ahora y
        // quedaba pasivo en la base sin ninguna forma de saber que ya podía
        // entrar. Reusa MemberLifecycleEmailService (best-effort, nunca
        // rompe este flujo si Resend falla).
        gymRepository.findById(gymId).ifPresent(gym -> {
            List<GymPlan> activePlans = gymPlanRepository.findByGymId(gymId).stream()
                    .filter(GymPlan::isActive)
                    .sorted(Comparator.comparing(GymPlan::getPriceClp, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
            String activationUrl = passwordAuthService.activationUrl(passwordAuthService.createInviteToken(member));
            memberLifecycleEmailService.sendMemberInviteWithPlans(gym, member, activePlans, activationUrl);
        });

        return toResponse(member);
    }

    // Carga masiva desde el Excel del admin (ver ImportMembersModal en el frontend) — cada fila
    // se procesa de forma INDEPENDIENTE (try/catch propio), nunca aborta el resto del lote si una
    // fila falla. El frontend manda esto en bloques de ~20 filas, no el archivo entero de una vez.
    public List<MemberImportRowResult> importMembers(Long gymId, List<MemberImportRow> rows) {
        if (rows.size() > MAX_IMPORT_ROWS) {
            throw new IllegalArgumentException("No se pueden importar más de " + MAX_IMPORT_ROWS + " filas por vez.");
        }
        Gym gym = gymRepository.findById(gymId).orElseThrow(() -> new GymNotFoundException(gymId));
        List<GymPlan> activeGymPlans =
                gymPlanRepository.findByGymId(gymId).stream().filter(GymPlan::isActive).toList();
        List<GymPlan> activePlansForInvite = activeGymPlans.stream()
                .sorted(Comparator.comparing(GymPlan::getPriceClp, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        // Solo planes ACTIVOS — un planId de un plan desactivado se rechaza igual que uno
        // inexistente (ver MemberImportRowService.importRow), nunca se acepta a medias.
        Map<Long, GymPlan> plansById = activeGymPlans.stream().collect(Collectors.toMap(GymPlan::getId, p -> p));

        // Solo caben tantas filas como cupos libres: las demas se informan fila por fila ("sin cupo") en vez de abortar
        // el bloque. El cupo se descuenta fila a fila, porque una fila repetida o invalida no ocupa lugar.
        int freeSlots = gymCapacityService.freeMemberSlots(gym);
        List<MemberImportRowResult> results = new ArrayList<>();
        for (MemberImportRow row : rows) {
            if (freeSlots <= 0) {
                results.add(new MemberImportRowResult(
                        row.email(), false, "Sin cupo: tu gimnasio llegó al límite de " + gym.getMaxUsers() + " socios."));
                continue;
            }
            try {
                // REQUIRES_NEW en MemberImportRowService — cada fila corre en su propia
                // transacción, así un error real de Postgres a mitad de un bloque nunca deja
                // "abortada" la transacción para las filas siguientes del mismo bloque.
                MemberImportRowResult result =
                        memberImportRowService.importRow(gymId, gym, row, activePlansForInvite, plansById);
                results.add(result);
                if (result.success()) {
                    freeSlots--;
                }
            } catch (Exception e) {
                log.error("Fila de importación falló para {}: {} (el detalle se omite: los errores de la base incluyen el correo completo)", LogMask.email(row.email()), e.getClass().getSimpleName());
                results.add(new MemberImportRowResult(row.email(), false, "Error inesperado al importar esta fila."));
            }
        }
        return results;
    }

    // Tope duro de página: aunque el cliente pida size=10000, nunca se devuelve más que esto.
    private static final int MAX_PAGE_SIZE = 50;

    /**
     * Lista paginada de socios de un gym con búsqueda por nombre/email y filtros de estado de pago
     * e invitación. El estado de membresía NO es una columna (se calcula al vuelo desde paidAt,
     * ver membershipStatus), así que filtrar/buscar/ordenar se hace en memoria sobre las filas
     * AppUser del gym (una sola query liviana) y recién la página pedida pasa por toResponses —
     * que es la parte cara (planes + reservas del período). Así el filtro usa EXACTAMENTE la
     * misma regla que muestra la fila, sin duplicarla en SQL. Orden: nombre (sin mayúsculas), id.
     */
    @Transactional(readOnly = true)
    public PageResponse<MemberResponse> searchMembers(
            Long gymId, String query, String status, String invite, int page, int size) {
        String needle = query == null ? "" : query.trim().toLowerCase();
        List<AppUser> matches = appUserRepository.findByGymIdAndRole(gymId, Role.MEMBER).stream()
                .filter(u -> status == null || status.isBlank() || status.equals(membershipStatus(u)))
                .filter(u -> invite == null || invite.isBlank() || invite.equals(inviteStatus(u)))
                .filter(u -> needle.isEmpty()
                        || contains(u.getName(), needle)
                        || contains(u.getEmail(), needle)
                        || matchesContactDigits(u, needle))
                .sorted(searchOrder(status))
                .toList();
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        int from = (int) Math.min((long) safePage * safeSize, matches.size());
        int to = Math.min(from + safeSize, matches.size());
        return new PageResponse<>(
                toResponses(matches.subList(from, to)), safePage, safeSize, matches.size(), to < matches.size());
    }

    // Orden de la lista: por defecto nombre. Con EXPIRING_SOON/EXPIRED el que vence antes (o venció hace más
    // tiempo) va primero, por su vencimiento FINAL (el de la renovación anticipada si la hay); sin pago (null) al
    // final. UNPAID no tiene fecha, queda por nombre. Desempate siempre nombre e id, para que la paginación sea estable.
    private static Comparator<AppUser> searchOrder(String status) {
        Comparator<AppUser> byName =
                Comparator.comparing((AppUser u) -> u.getName() == null ? "" : u.getName().toLowerCase())
                        .thenComparing(AppUser::getId);
        if ("EXPIRING_SOON".equals(status) || "EXPIRED".equals(status)) {
            return Comparator.comparing(
                            (AppUser u) -> {
                                ZonedDateTime end = MembershipPeriods.finalEnd(u);
                                return end == null ? null : end.toInstant();
                            },
                            Comparator.nullsLast(Comparator.<Instant>naturalOrder()))
                    .thenComparing(byName);
        }
        return byName;
    }

    // Búsqueda por RUT o celular: solo con 3+ dígitos ("k" cuenta, es el dígito verificador) y sin importar puntos, guión, +56 o espacios.
    private static boolean matchesContactDigits(AppUser user, String needle) {
        String digits = needle.replaceAll("[^0-9k]", "");
        if (digits.length() < 3) {
            return false;
        }
        return onlyDigits(user.getRut()).contains(digits) || onlyDigits(user.getPhone()).contains(digits);
    }

    private static String onlyDigits(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^0-9k]", "");
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }

    /** Conteos del gym completo para las calugas — mismo criterio que membershipStatus/inviteStatus. */
    @Transactional(readOnly = true)
    public MemberSummaryResponse memberSummary(Long gymId) {
        long active = 0;
        long expiringSoon = 0;
        long expired = 0;
        long unpaid = 0;
        long invitedPending = 0;
        List<AppUser> members = appUserRepository.findByGymIdAndRole(gymId, Role.MEMBER);
        for (AppUser user : members) {
            switch (membershipStatus(user)) {
                case "ACTIVE" -> active++;
                case "EXPIRING_SOON" -> expiringSoon++;
                case "EXPIRED" -> expired++;
                default -> unpaid++;
            }
            if ("PENDING".equals(inviteStatus(user))) {
                invitedPending++;
            }
        }
        return new MemberSummaryResponse(members.size(), active, expiringSoon, expired, unpaid, invitedPending);
    }

    /** Solo los emails (para que el modal de importación detecte socios ya existentes) — liviano
     *  a propósito: no pasa por toResponses, así que no paga planes ni reservas. */
    @Transactional(readOnly = true)
    public List<String> memberEmails(Long gymId) {
        return appUserRepository.findByGymIdAndRole(gymId, Role.MEMBER).stream()
                .map(AppUser::getEmail)
                .filter(Objects::nonNull)
                .toList();
    }

    // Borrado permanente — a diferencia de revokePlan (solo le saca el plan), esto elimina
    // la fila del socio por completo. reservation y payment tienen ON DELETE CASCADE sobre
    // member_id (reservation desde siempre, payment desde V18), así que el borrado de la fila
    // ya se lleva puesto su historial de reservas y de pagos sin borrarlos a mano acá. Solo
    // super-admin puede hacerlo (GymController), nunca el propio gym-admin — y solo aplica a
    // un MEMBER real, nunca a un GYM_ADMIN/SUPER_ADMIN (evita borrar por error al dueño del gym).
    public void deleteMember(Long gymId, Long memberId) {
        AppUser member = appUserRepository
                .findByIdAndGymId(memberId, gymId)
                .filter(u -> u.getRole() == Role.MEMBER)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
        appUserRepository.delete(member);
    }

    // Antes /member (autoservicio del socio) nunca leía de vuelta su propio
    // plan/paidAt reales — el signal `membership` del frontend arrancaba
    // siempre en "none" sin importar lo que un admin ya hubiera marcado
    // como pagado. Reportado por el usuario: un socio con plan "Activo"
    // visto desde el panel del admin veía "Elige tu plan" en su propia
    // vista.
    @Transactional(readOnly = true)
    public MemberResponse getOwnMembership(Long userId) {
        AppUser user = appUserRepository.findById(userId).orElseThrow(() -> new MemberNotFoundException(userId));
        return toResponse(user);
    }

    // Mismo umbral que expirySoon en member.ts (0 < díasRestantes <= 3).
    private static final int EXPIRING_SOON_DAYS = 3;

    // Eje independiente de membershipStatus (pago) — mide si este socio vino
    // de una alta manual del admin y, si vino de ahí, si ya llegó a activar
    // su cuenta con Google. invitedAt se setea una sola vez, en createMember;
    // el alta pública (/j/{slug}) nunca lo toca, así que un socio auto-
    // registrado siempre da null acá.
    private String inviteStatus(AppUser user) {
        if (user.getInvitedAt() == null) {
            return null;
        }
        return user.getGoogleSub() != null ? "REGISTERED" : "PENDING";
    }

    // Usado como gate real de autorización por ReservationService.book() — hasta que esto
    // existió, cualquier socio podía reservar clases gratis sin importar si había pagado o
    // no (membershipStatus era puramente informativo para la UI). Reusa el mismo cálculo,
    // nunca duplica la lógica de "1 mes calendario desde paidAt".
    public boolean hasActiveMembership(AppUser user) {
        String status = membershipStatus(user);
        return "ACTIVE".equals(status) || "EXPIRING_SOON".equals(status);
    }

    // Segundo gate real de ReservationService.book(), separado de hasActiveMembership: un socio
    // con plan de cupo limitado podía seguir reservando clases pasado su límite mensual llamando
    // directo a la API — el frontend sí lo bloqueaba (member.ts quotaExhausted) pero el backend
    // nunca lo chequeaba. Reusa el mismo criterio de período que membershipStatus/toResponses
    // (ver MembershipPeriods), nunca lo duplica.
    public boolean hasQuotaAvailable(AppUser user) {
        Integer remaining = remainingClasses(user);
        return remaining == null || remaining > 0;
    }

    /** Igual, pero contra el período al que cuenta ESA clase: una fecha del período renovado por adelantado gasta el cupo
     *  nuevo (completo); una anterior al vencimiento actual gasta el actual. */
    public boolean hasQuotaAvailable(AppUser user, LocalDate classDate) {
        Integer remaining = remainingClasses(user, classDate);
        return remaining == null || remaining > 0;
    }

    /** Clases que le quedan en el período actual; null = sin tope (plan libre, sin plan o sin pago).
     *  Mismo cálculo que usa hasQuotaAvailable y que el aviso "usaste todas tus clases" de
     *  ReservationService.book — nunca duplicarlo. */
    public Integer remainingClasses(AppUser user) {
        if (user.getPlanId() == null || user.getPaidAt() == null) {
            return null;
        }
        return remainingInPeriod(user, MembershipPeriods.currentIndex(user, ZonedDateTime.now(GYM_ZONE)));
    }

    /** Clases que le quedan en el período al que cuenta una clase de esa fecha (ver MembershipPeriods.indexOfClassDate). */
    public Integer remainingClasses(AppUser user, LocalDate classDate) {
        if (user.getPlanId() == null || user.getPaidAt() == null) {
            return null;
        }
        ZonedDateTime now = ZonedDateTime.now(GYM_ZONE);
        int index = MembershipPeriods.indexOfClassDate(user, classDate, now);
        if (index > MembershipPeriods.paidPeriods(user)) {
            return null; // fuera de lo pagado no hay cupo que gastar: lo rechaza isWithinPaidPeriod con su propio mensaje
        }
        return remainingInPeriod(user, index);
    }

    /** Fecha de vencimiento (fin exclusivo, fecha de Chile) del período al que cuenta una clase de esa fecha; para mensajes. */
    public LocalDate periodEndForClass(AppUser user, LocalDate classDate) {
        ZonedDateTime anchor = MembershipPeriods.anchor(user);
        int index = Math.min(
                MembershipPeriods.indexOfClassDate(user, classDate, ZonedDateTime.now(GYM_ZONE)),
                MembershipPeriods.paidPeriods(user));
        return MembershipPeriods.endOf(anchor, index).toLocalDate();
    }

    /** Clases que le quedan en el período PAGADO POR ADELANTADO (cupo completo menos lo ya reservado en él); null = sin cola o sin tope. */
    public Integer remainingClassesInQueuedPeriod(AppUser user) {
        ZonedDateTime now = ZonedDateTime.now(GYM_ZONE);
        if (user.getPlanId() == null || !MembershipPeriods.hasQueued(user, now)) {
            return null;
        }
        return remainingInPeriod(user, MembershipPeriods.currentIndex(user, now) + 1);
    }

    private Integer remainingInPeriod(AppUser user, int index) {
        GymPlan plan = gymPlanRepository.findById(user.getPlanId()).orElse(null);
        if (plan == null || plan.getMonthlyClasses() == null) {
            return null;
        }
        ZonedDateTime anchor = MembershipPeriods.anchor(user);
        LocalDate start = MembershipPeriods.startOf(anchor, index).toLocalDate();
        LocalDate endInclusive = MembershipPeriods.endOf(anchor, index).toLocalDate().minusDays(1);
        int used = reservationRepository.countByMemberIdAndStatusAndClassDateBetween(
                user.getId(), ReservationStatus.BOOKED, start, endInclusive);
        // Lo que traía de una importación (o declaró el admin) vale solo para el PRIMER período de la cadena.
        int usedAtImport = index == 1 && user.getUsedSessionsAtImport() != null ? user.getUsedSessionsAtImport() : 0;
        return plan.getMonthlyClasses() - (used + usedAtImport);
    }

    /** Período del socio: de la fecha de inicio al día anterior al siguiente borde (ambos inclusive). */
    public record PeriodRange(LocalDate start, LocalDate endInclusive) {}

    /** Período ACTUAL (el de la renovación anticipada, si ya empezó); null si nunca pagó. Única cuenta del período, nunca duplicarla. */
    public PeriodRange currentPeriod(AppUser user) {
        if (user.getPaidAt() == null) {
            return null;
        }
        ZonedDateTime now = ZonedDateTime.now(GYM_ZONE);
        return new PeriodRange(
                MembershipPeriods.currentStart(user, now).toLocalDate(),
                MembershipPeriods.currentEnd(user, now).toLocalDate().minusDays(1));
    }

    /** Período pagado por adelantado (parte en el vencimiento del actual y llega al vencimiento final); null si no renovó antes. */
    public PeriodRange queuedPeriod(AppUser user) {
        ZonedDateTime now = ZonedDateTime.now(GYM_ZONE);
        if (!MembershipPeriods.hasQueued(user, now)) {
            return null;
        }
        return new PeriodRange(
                MembershipPeriods.currentEnd(user, now).toLocalDate(),
                MembershipPeriods.finalEnd(user).toLocalDate().minusDays(1));
    }

    /** Último día cubierto por lo pagado (cola incluida), o null si nunca pagó. */
    public static LocalDate paidUntil(AppUser user) {
        ZonedDateTime end = MembershipPeriods.finalEnd(user);
        return end != null ? end.toLocalDate().minusDays(1) : null;
    }

    /**
     * ¿Cae esa clase dentro de lo que el socio pagó (hasta el vencimiento FINAL, cola incluida)? Reservar fechas
     * posteriores al vencimiento
     * se saltaba el cupo mensual (la cuenta solo mira el período actual) y dejaba reservas vivas
     * después de que el plan venciera (auditoría 2026-10-17). Sin pago registrado no hay período que
     * validar: de eso se encarga hasActiveMembership. Estático a propósito: lo comparten la reserva y la
     * lista de espera sin acoplarse entre servicios.
     */
    public static boolean isWithinPaidPeriod(AppUser user, LocalDate classDate) {
        LocalDate until = paidUntil(user);
        return until == null || !classDate.isAfter(until);
    }

    private String membershipStatus(AppUser user) {
        Instant paidAt = user.getPaidAt();
        if (paidAt == null) {
            return "UNPAID";
        }
        ZonedDateTime now = ZonedDateTime.now(GYM_ZONE);
        ZonedDateTime periodEnd = periodEnd(user);
        if (!now.isBefore(periodEnd)) {
            return "EXPIRED";
        }
        long daysRemaining = Duration.between(now, periodEnd).toDays();
        return daysRemaining <= EXPIRING_SOON_DAYS ? "EXPIRING_SOON" : "ACTIVE";
    }

    // Vencimiento FINAL (cola de renovación anticipada incluida): el estado "por vencer"/"vencido" se decide con este.
    // Usado también por MembershipReminderJob y el CSV de desvinculación — nunca duplicar la cuenta de períodos
    // (ver MembershipPeriods).
    public ZonedDateTime periodEnd(AppUser user) {
        return MembershipPeriods.finalEnd(user);
    }

    private MemberResponse toResponse(AppUser user) {
        return toResponses(List.of(user)).get(0);
    }

    /** Arma la respuesta de una lista de socios SIN N+1 — antes esto era toResponse() llamado
     *  una vez por socio, y cada llamada disparaba su propio gymPlanRepository.findById() +
     *  su propio reservationRepository.count(). Con un gym de varios cientos de socios eso eran
     *  cientos de queries extra en una sola carga de la pestaña Socios (reportado por el
     *  usuario como lentitud de carga, mismo patrón que ya se arregló en GymSummaryResponse/
     *  AnalyticsService). Ahora: 1 query batch de planes + 1 query batch de reservas, el resto
     *  se resuelve en memoria. Un solo socio (createMember/getOwnMembership) pasa por acá
     *  también, como lista de 1 — el costo extra de armar los maps es despreciable. */
    private List<MemberResponse> toResponses(List<AppUser> users) {
        List<Long> planIds = users.stream()
                .flatMap(u -> java.util.stream.Stream.of(u.getPlanId(), u.getPendingPlanId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, GymPlan> plansById = planIds.isEmpty()
                ? Map.of()
                : gymPlanRepository.findAllById(planIds).stream().collect(Collectors.toMap(GymPlan::getId, p -> p));

        // Período ACTUAL [curStart, curEnd) y, si renovó por adelantado, el período EN COLA [curEnd, queuedEnd) — fechas de
        // Chile, fin exclusivo. Todo sale de MembershipPeriods.
        ZonedDateTime nowZoned = ZonedDateTime.now(GYM_ZONE);
        record Period(Long memberId, int curIndex, LocalDate curStart, LocalDate curEnd, LocalDate queuedEnd) {}
        List<Period> periods = new ArrayList<>();
        for (AppUser user : users) {
            GymPlan plan = user.getPlanId() != null ? plansById.get(user.getPlanId()) : null;
            if (plan != null && plan.getMonthlyClasses() != null && user.getPaidAt() != null) {
                periods.add(new Period(
                        user.getId(),
                        MembershipPeriods.currentIndex(user, nowZoned),
                        MembershipPeriods.currentStart(user, nowZoned).toLocalDate(),
                        MembershipPeriods.currentEnd(user, nowZoned).toLocalDate(),
                        MembershipPeriods.hasQueued(user, nowZoned)
                                ? MembershipPeriods.finalEnd(user).toLocalDate()
                                : null));
            }
        }

        Map<Long, Long> usedByMember = Map.of();
        Map<Long, Long> usedQueuedByMember = Map.of();
        if (!periods.isEmpty()) {
            LocalDate minStart = periods.stream().map(Period::curStart).min(LocalDate::compareTo).orElseThrow();
            LocalDate maxEnd = periods.stream()
                    .map(p -> p.queuedEnd() != null ? p.queuedEnd() : p.curEnd())
                    .max(LocalDate::compareTo)
                    .orElseThrow();
            List<Long> memberIds = periods.stream().map(Period::memberId).toList();
            Map<Long, List<Reservation>> reservationsByMember = reservationRepository
                    .findByMemberIdInAndStatusAndClassDateBetween(memberIds, ReservationStatus.BOOKED, minStart, maxEnd)
                    .stream()
                    .collect(Collectors.groupingBy(Reservation::getMemberId));
            usedByMember = periods.stream()
                    .collect(Collectors.toMap(
                            Period::memberId,
                            p -> reservationsByMember.getOrDefault(p.memberId(), List.of()).stream()
                                    .filter(r -> !r.getClassDate().isBefore(p.curStart()) && r.getClassDate().isBefore(p.curEnd()))
                                    .count()));
            usedQueuedByMember = periods.stream()
                    .filter(p -> p.queuedEnd() != null)
                    .collect(Collectors.toMap(
                            Period::memberId,
                            p -> reservationsByMember.getOrDefault(p.memberId(), List.of()).stream()
                                    .filter(r -> !r.getClassDate().isBefore(p.curEnd()) && r.getClassDate().isBefore(p.queuedEnd()))
                                    .count()));
        }

        // Clases fijas: 1 query por página (no una por socio) + 1 de los bloques involucrados.
        List<Long> allMemberIds = users.stream().map(AppUser::getId).toList();
        Map<Long, List<MemberFixedClass>> fixedByMember = allMemberIds.isEmpty()
                ? Map.of()
                : memberFixedClassRepository.findByMemberIdIn(allMemberIds).stream()
                        .collect(Collectors.groupingBy(MemberFixedClass::getMemberId));
        Map<Long, GymBlock> blocksById = fixedByMember.isEmpty()
                ? Map.of()
                : gymBlockRepository
                        .findAllById(fixedByMember.values().stream()
                                .flatMap(List::stream)
                                .map(MemberFixedClass::getGymBlockId)
                                .distinct()
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(GymBlock::getId, b -> b));

        List<MemberResponse> responses = new ArrayList<>();
        for (AppUser user : users) {
            String planName = null;
            Instant planEndDate = null;
            Integer monthlyClasses = null;
            Integer sessionsRemaining = null;
            Instant currentPeriodEnd = null;
            Instant queuedPeriodEnd = null;
            Integer queuedSessionsRemaining = null;
            boolean canRenewEarly = false;
            LocalDate earlyRenewalStartsOn = null;
            Long planId = user.getPlanId();
            Instant paidAt = user.getPaidAt();
            if (planId != null && paidAt != null) {
                // planEndDate = vencimiento FINAL (el que ve el socio); el del período actual va aparte.
                planEndDate = MembershipPeriods.finalEnd(user).toInstant();
                currentPeriodEnd = MembershipPeriods.currentEnd(user, nowZoned).toInstant();
                boolean queued = MembershipPeriods.hasQueued(user, nowZoned);
                if (queued) {
                    queuedPeriodEnd = planEndDate;
                } else if (MembershipPeriods.ADVANCE_PLANS_ENABLED && !MembershipPeriods.isExpired(user, nowZoned)) {
                    canRenewEarly = true;
                    earlyRenewalStartsOn = MembershipPeriods.currentEnd(user, nowZoned).toLocalDate();
                }
                GymPlan plan = plansById.get(planId);
                if (plan != null) {
                    planName = plan.getName();
                    monthlyClasses = plan.getMonthlyClasses();
                    if (monthlyClasses != null) {
                        long used = usedByMember.getOrDefault(user.getId(), 0L);
                        // Lo importado/declarado por el admin vale solo para el primer período de la cadena.
                        int usedAtImport = MembershipPeriods.currentIndex(user, nowZoned) == 1
                                        && user.getUsedSessionsAtImport() != null
                                ? user.getUsedSessionsAtImport()
                                : 0;
                        sessionsRemaining = (int) Math.max(0, monthlyClasses - used - usedAtImport);
                        if (queued) {
                            queuedSessionsRemaining =
                                    (int) Math.max(0, monthlyClasses - usedQueuedByMember.getOrDefault(user.getId(), 0L));
                            // Día del borde (de las 00:00 a la hora de vencimiento): el período actual sigue vigente por
                            // instante pero ya no tiene fechas reservables (todas son < hoy), y una clase de hoy cuenta contra el
                            // período nuevo. Mostrar el saldo del actual sería engañoso: se muestra el del siguiente.
                            if (!MembershipPeriods.currentEnd(user, nowZoned).toLocalDate().isAfter(nowZoned.toLocalDate())) {
                                sessionsRemaining = queuedSessionsRemaining;
                            }
                        }
                    }
                }
            }
            responses.add(new MemberResponse(
                    user.getId(),
                    user.getName(),
                    user.getEmail(),
                    user.getRole(),
                    user.getGymId(),
                    user.isActive(),
                    user.getCreatedAt(),
                    user.getPlanId(),
                    user.getPaidAt(),
                    membershipStatus(user),
                    planName,
                    planEndDate,
                    monthlyClasses,
                    sessionsRemaining,
                    user.getPhotoUrl(),
                    inviteStatus(user),
                    FixedClassFormat.summary(fixedByMember.getOrDefault(user.getId(), List.of()).stream()
                            .map(f -> blocksById.get(f.getGymBlockId()))
                            .filter(Objects::nonNull)
                            .toList()),
                    user.getRut(),
                    user.getAddress(),
                    user.getBirthDate(),
                    user.getPhone(),
                    user.getEmergencyContactName(),
                    user.getEmergencyContactPhone(),
                    user.isProfileComplete(),
                    currentPeriodEnd,
                    queuedPeriodEnd,
                    queuedSessionsRemaining,
                    canRenewEarly,
                    earlyRenewalStartsOn,
                    user.getPendingPlanId(),
                    user.getPendingPlanId() != null && plansById.get(user.getPendingPlanId()) != null
                            ? plansById.get(user.getPendingPlanId()).getName()
                            : null));
        }
        return responses;
    }
}
