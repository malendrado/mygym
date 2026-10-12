package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.AttendanceSummaryResponse;
import com.cortesdev.mygym.models.dto.AttendanceSummaryResponse.ClassStat;
import com.cortesdev.mygym.models.dto.AttendanceSummaryResponse.Totals;
import com.cortesdev.mygym.models.dto.AttendanceSummaryResponse.WeekdayStat;
import com.cortesdev.mygym.models.dto.MemberAttendanceDetail;
import com.cortesdev.mygym.models.dto.MemberAttendanceRow;
import com.cortesdev.mygym.models.dto.PageResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.services.exception.GymBlockNotFoundException;
import com.cortesdev.mygym.services.exception.InvalidAttendanceException;
import com.cortesdev.mygym.services.exception.MemberNotFoundException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resúmenes de asistencia (reservaron vs vinieron) por período — pedido del usuario 2026-10-12.
 * "No vino" no es un estado guardado: es una reserva BOOKED de una clase que ya terminó y sin
 * check-in (ver Reservation.checkedInAt). Todo se calcula con UNA consulta de reservas por llamada
 * (rango acotado a MAX_RANGE_DAYS) y se agrupa en memoria; los socios se traen con un solo
 * findAllById, y solo los de la página que se devuelve.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceSummaryService {

    private static final ZoneId GYM_ZONE = ZoneId.of("America/Santiago");

    // Un mes con margen. Acota la consulta: sin esto cualquiera podría pedir años de reservas.
    static final int MAX_RANGE_DAYS = 62;
    // Un socio con 1 o 2 reservas no entra al ranking: una sola falta no lo hace "el que más falta".
    static final int MIN_BOOKINGS_FOR_RANKING = 3;
    static final int TOP_ABSENTEES = 10;
    static final int DEFAULT_PAGE_SIZE = 25;
    static final int MAX_PAGE_SIZE = 50;

    private final GymBlockRepository gymBlockRepository;
    private final ReservationRepository reservationRepository;
    private final AppUserRepository appUserRepository;

    public AttendanceSummaryResponse summary(Long gymId, LocalDate from, LocalDate to) {
        return summary(gymId, from, to, null);
    }

    /**
     * previousFrom (opcional) es donde empieza el período con el que se compara; siempre termina el
     * día antes de `from`. Sin él, se usa uno del mismo largo (la semana pasada). Con meses hace falta:
     * febrero (28 días) se compara con enero completo, no con los 28 días anteriores.
     */
    public AttendanceSummaryResponse summary(Long gymId, LocalDate from, LocalDate to, LocalDate previousFromParam) {
        validateRange(from, to);
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate previousFrom = previousFromParam != null ? previousFromParam : from.minusDays(days);
        LocalDate previousTo = from.minusDays(1);
        validateRange(previousFrom, previousTo);

        Map<Long, GymBlock> blocks = blocksOf(gymId);
        // Una sola consulta cubre el período y el anterior; se separan en memoria por fecha.
        List<Reservation> all = fetch(blocks, previousFrom, to);
        ZonedDateTime now = ZonedDateTime.now(GYM_ZONE);
        List<Reservation> current = new ArrayList<>();
        List<Reservation> previous = new ArrayList<>();
        for (Reservation r : all) {
            if (!ended(r, blocks, now)) {
                continue;
            }
            (r.getClassDate().isBefore(from) ? previous : current).add(r);
        }

        Map<Long, int[]> byBlock = new LinkedHashMap<>();
        Map<DayOfWeek, int[]> byDay = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            byDay.put(day, new int[2]);
        }
        for (Reservation r : current) {
            int attended = r.getCheckedInAt() != null ? 1 : 0;
            int[] block = byBlock.computeIfAbsent(r.getGymBlockId(), k -> new int[2]);
            block[0]++;
            block[1] += attended;
            int[] day = byDay.get(blocks.get(r.getGymBlockId()).getDayOfWeek());
            day[0]++;
            day[1] += attended;
        }

        List<ClassStat> byClass = byBlock.entrySet().stream()
                .map(e -> {
                    GymBlock b = blocks.get(e.getKey());
                    return new ClassStat(
                            b.getId(), b.getLabel(), b.getDayOfWeek(), b.getStartTime(), e.getValue()[0], e.getValue()[1]);
                })
                .sorted(Comparator.comparingDouble((ClassStat c) -> (double) c.attended() / c.booked())
                        .thenComparing(ClassStat::booked, Comparator.reverseOrder())
                        .thenComparing(ClassStat::blockId))
                .toList();
        List<WeekdayStat> byWeekday = byDay.entrySet().stream()
                .map(e -> new WeekdayStat(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();

        List<Aggregate> ranking = aggregateByMember(current).stream()
                .filter(a -> a.booked >= MIN_BOOKINGS_FOR_RANKING && a.missed() > 0)
                .sorted(WORST_FIRST)
                .limit(TOP_ABSENTEES)
                .toList();

        return new AttendanceSummaryResponse(
                totals(current), totals(previous), byClass, byWeekday, toRows(ranking));
    }

    /**
     * Los socios detrás de un número del resumen. blockId = una fila de "por clase"; weekday = una
     * barra de "por día"; onlyMissed = la cifra "No vinieron". Sin filtros = "Reservaron"/"Vinieron".
     * Más faltas primero; empate, peor tasa primero.
     */
    public PageResponse<MemberAttendanceRow> members(
            Long gymId,
            LocalDate from,
            LocalDate to,
            Long blockId,
            DayOfWeek weekday,
            boolean onlyMissed,
            int page,
            int size) {
        validateRange(from, to);
        Map<Long, GymBlock> blocks = blocksOf(gymId);
        if (blockId != null && !blocks.containsKey(blockId)) {
            // Un bloque de otro gym se trata igual que uno inexistente: no se revela que existe.
            throw new GymBlockNotFoundException(gymId, blockId);
        }
        int pageSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int pageIndex = Math.max(page, 0);

        ZonedDateTime now = ZonedDateTime.now(GYM_ZONE);
        List<Reservation> filtered = fetch(blocks, from, to).stream()
                .filter(r -> ended(r, blocks, now))
                .filter(r -> blockId == null || r.getGymBlockId().equals(blockId))
                .filter(r -> weekday == null || blocks.get(r.getGymBlockId()).getDayOfWeek() == weekday)
                .toList();
        List<Aggregate> members = aggregateByMember(filtered).stream()
                .filter(a -> !onlyMissed || a.missed() > 0)
                .sorted(WORST_FIRST)
                .toList();

        int start = (int) Math.min((long) pageIndex * pageSize, members.size());
        int end = Math.min(start + pageSize, members.size());
        return new PageResponse<>(toRows(members.subList(start, end)), pageIndex, pageSize, members.size(), end < members.size());
    }

    /** Cada reserva de UN socio en el período, con si vino y cómo. 404 si no es socio de ese gym. */
    public MemberAttendanceDetail memberDetail(Long gymId, Long memberId, LocalDate from, LocalDate to) {
        validateRange(from, to);
        AppUser member = appUserRepository
                .findByIdAndGymId(memberId, gymId)
                .filter(u -> u.getRole() == Role.MEMBER)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
        Map<Long, GymBlock> blocks = blocksOf(gymId);
        List<Reservation> reservations = reservationRepository.findByMemberIdAndClassDateBetween(memberId, from, to).stream()
                .filter(r -> r.getStatus() == ReservationStatus.BOOKED)
                .filter(r -> blocks.containsKey(r.getGymBlockId()))
                .toList();

        ZonedDateTime now = ZonedDateTime.now(GYM_ZONE);
        List<MemberAttendanceDetail.Row> rows = reservations.stream()
                .sorted(Comparator.comparing(Reservation::getClassDate)
                        .thenComparing(r -> blocks.get(r.getGymBlockId()).getStartTime())
                        .reversed())
                .map(r -> {
                    GymBlock b = blocks.get(r.getGymBlockId());
                    return new MemberAttendanceDetail.Row(
                            r.getId(),
                            r.getClassDate(),
                            b.getLabel(),
                            b.getStartTime(),
                            ended(r, blocks, now),
                            r.getCheckedInAt() != null,
                            r.getCheckinMethod(),
                            r.getCheckedInAt(),
                            ReservationService.isAttendanceEditable(r.getClassDate(), b.getStartTime(), b.getEndTime()));
                })
                .toList();

        List<Reservation> endedOnes = reservations.stream().filter(r -> ended(r, blocks, now)).toList();
        Aggregate agg = aggregateByMember(endedOnes).stream().findFirst().orElse(new Aggregate(memberId));
        MemberAttendanceRow header = new MemberAttendanceRow(
                member.getId(),
                member.getName(),
                member.getPhotoUrl(),
                member.getPhone(),
                agg.booked,
                agg.attended,
                agg.missed(),
                agg.lastMissedDate);
        return new MemberAttendanceDetail(header, rows);
    }

    // ---- helpers ----

    private static final Comparator<Aggregate> WORST_FIRST = Comparator.comparingInt(Aggregate::missed)
            .reversed()
            .thenComparingDouble((Aggregate a) -> (double) a.attended / a.booked)
            .thenComparing(a -> a.memberId);

    /** Contadores de un socio sobre reservas ya terminadas. */
    private static final class Aggregate {
        final Long memberId;
        int booked;
        int attended;
        LocalDate lastMissedDate;

        Aggregate(Long memberId) {
            this.memberId = memberId;
        }

        int missed() {
            return booked - attended;
        }
    }

    private static List<Aggregate> aggregateByMember(List<Reservation> endedReservations) {
        Map<Long, Aggregate> byMember = new HashMap<>();
        for (Reservation r : endedReservations) {
            Aggregate a = byMember.computeIfAbsent(r.getMemberId(), Aggregate::new);
            a.booked++;
            if (r.getCheckedInAt() != null) {
                a.attended++;
            } else if (a.lastMissedDate == null || r.getClassDate().isAfter(a.lastMissedDate)) {
                a.lastMissedDate = r.getClassDate();
            }
        }
        return new ArrayList<>(byMember.values());
    }

    /** Un solo findAllById para todos los socios de la lista; los que ya no existen se omiten. */
    private List<MemberAttendanceRow> toRows(List<Aggregate> aggregates) {
        if (aggregates.isEmpty()) {
            return List.of();
        }
        Map<Long, AppUser> users = appUserRepository
                .findAllById(aggregates.stream().map(a -> a.memberId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(AppUser::getId, u -> u));
        return aggregates.stream()
                .filter(a -> users.containsKey(a.memberId))
                .map(a -> {
                    AppUser u = users.get(a.memberId);
                    return new MemberAttendanceRow(
                            u.getId(), u.getName(), u.getPhotoUrl(), u.getPhone(), a.booked, a.attended, a.missed(), a.lastMissedDate);
                })
                .toList();
    }

    private static Totals totals(List<Reservation> endedReservations) {
        int attended = (int) endedReservations.stream().filter(r -> r.getCheckedInAt() != null).count();
        return new Totals(endedReservations.size(), attended, endedReservations.size() - attended);
    }

    private Map<Long, GymBlock> blocksOf(Long gymId) {
        return gymBlockRepository.findByGymId(gymId).stream().collect(Collectors.toMap(GymBlock::getId, b -> b));
    }

    private List<Reservation> fetch(Map<Long, GymBlock> blocks, LocalDate from, LocalDate to) {
        if (blocks.isEmpty()) {
            return List.of();
        }
        return reservationRepository.findByGymBlockIdInAndClassDateBetweenAndStatus(
                List.copyOf(blocks.keySet()), from, to, ReservationStatus.BOOKED);
    }

    /** Mismo criterio que ReservationService.isPastOccurrence: la clase termina a su hora de fin, en hora de Chile. */
    private static boolean ended(Reservation r, Map<Long, GymBlock> blocks, ZonedDateTime now) {
        GymBlock block = blocks.get(r.getGymBlockId());
        return block != null && ZonedDateTime.of(r.getClassDate(), block.getEndTime(), GYM_ZONE).isBefore(now);
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new InvalidAttendanceException("El rango de fechas no es válido.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new InvalidAttendanceException("Elige un período de hasta " + MAX_RANGE_DAYS + " días.");
        }
    }
}
