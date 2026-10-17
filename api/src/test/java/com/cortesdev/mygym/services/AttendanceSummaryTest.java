package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.CheckinMethod;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.AttendanceSummaryResponse;
import com.cortesdev.mygym.models.dto.MemberAttendanceDetail;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.services.exception.GymBlockNotFoundException;
import com.cortesdev.mygym.services.exception.InvalidAttendanceException;
import com.cortesdev.mygym.services.exception.MemberNotFoundException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Resúmenes de asistencia (Mockito, sin DB): solo clases terminadas, ranking, período anterior,
 *  filtros y paginación de la lista de socios, y aislamiento entre gimnasios. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceSummaryTest {

    private static final ZoneId ZONE = ZoneId.of("America/Santiago");
    private static final Long GYM_ID = 7L;
    private static final Long GAP = 11L; // lunes 19:00
    private static final Long CROSSFIT = 12L; // viernes 07:00

    @Mock private GymBlockRepository gymBlockRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private AppUserRepository appUserRepository;
    @InjectMocks private AttendanceSummaryService service;

    private final LocalDate today = ZonedDateTime.now(ZONE).toLocalDate();
    private final LocalDate from = today.minusDays(6);
    private final LocalDate to = today;
    private List<Reservation> reservations;
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        reservations = new ArrayList<>();
        when(gymBlockRepository.findByGymId(GYM_ID))
                .thenReturn(List.of(
                        block(GAP, "GAP", DayOfWeek.MONDAY, LocalTime.of(19, 0), LocalTime.of(20, 0)),
                        block(CROSSFIT, "CrossFit", DayOfWeek.FRIDAY, LocalTime.of(7, 0), LocalTime.of(8, 0))));
        when(reservationRepository.findBookedSlotsInRange(any(), any(), any()))
                .thenAnswer(inv -> {
                    LocalDate f = inv.getArgument(1);
                    LocalDate t = inv.getArgument(2);
                    return reservations.stream()
                            .filter(r -> !r.getClassDate().isBefore(f) && !r.getClassDate().isAfter(t))
                            .map(r -> new com.cortesdev.mygym.repositories.BookedSlot(
                                    r.getGymBlockId(), r.getMemberId(), r.getClassDate(), r.getCheckedInAt()))
                            .toList();
                });
        when(appUserRepository.findAllById(any())).thenAnswer(inv -> {
            Iterable<Long> ids = inv.getArgument(0);
            List<AppUser> users = new ArrayList<>();
            for (Long id : ids) {
                users.add(user(id));
            }
            return users;
        });
    }

    private GymBlock block(Long id, String label, DayOfWeek day, LocalTime start, LocalTime end) {
        return GymBlock.builder()
                .id(id)
                .gymId(GYM_ID)
                .label(label)
                .dayOfWeek(day)
                .startTime(start)
                .endTime(end)
                .active(true)
                .build();
    }

    private AppUser user(Long id) {
        return AppUser.builder().id(id).gymId(GYM_ID).role(Role.MEMBER).name("Socio " + id).phone("+569" + id).build();
    }

    /** Reserva BOOKED de una clase de hace `daysAgo` días; attended = con check-in. */
    private Reservation res(Long blockId, long memberId, long daysAgo, boolean attended) {
        Reservation r = Reservation.builder()
                .id(nextId++)
                .gymBlockId(blockId)
                .memberId(memberId)
                .classDate(today.minusDays(daysAgo))
                .status(ReservationStatus.BOOKED)
                .checkedInAt(attended ? Instant.now() : null)
                .checkinMethod(attended ? CheckinMethod.QR : null)
                .build();
        reservations.add(r);
        return r;
    }

    // --- resumen ---

    @Test
    void noCuentaClasesQueNoTerminaron() {
        res(GAP, 1, 1, true);
        res(GAP, 2, 1, false);
        res(GAP, 3, -1, false); // mañana: ni empezó

        AttendanceSummaryResponse s = service.summary(GYM_ID, from, today.plusDays(2));

        assertThat(s.totals().booked()).isEqualTo(2);
        assertThat(s.totals().attended()).isEqualTo(1);
        assertThat(s.totals().missed()).isEqualTo(1);
    }

    @Test
    void sinReservasTerminadasTodoEnCeroYSinFilas() {
        AttendanceSummaryResponse s = service.summary(GYM_ID, from, to);

        assertThat(s.totals().booked()).isZero();
        assertThat(s.byClass()).isEmpty();
        assertThat(s.topAbsentees()).isEmpty();
        assertThat(s.byWeekday()).hasSize(7);
    }

    @Test
    void porClaseVaDePeorAMejorTasaYLlevaElBloque() {
        res(GAP, 1, 1, true);
        res(GAP, 2, 1, true);
        res(CROSSFIT, 1, 2, false);
        res(CROSSFIT, 2, 2, true);

        AttendanceSummaryResponse s = service.summary(GYM_ID, from, to);

        assertThat(s.byClass()).extracting("blockId").containsExactly(CROSSFIT, GAP);
        assertThat(s.byClass().get(0).booked()).isEqualTo(2);
        assertThat(s.byClass().get(0).attended()).isEqualTo(1);
    }

    @Test
    void porDiaDeLaSemanaSumaPorElDiaDeLaClase() {
        res(GAP, 1, 1, true);
        res(GAP, 2, 3, false);
        res(CROSSFIT, 3, 2, true);

        AttendanceSummaryResponse s = service.summary(GYM_ID, from, to);

        var monday = s.byWeekday().stream().filter(d -> d.weekday() == DayOfWeek.MONDAY).findFirst().orElseThrow();
        var friday = s.byWeekday().stream().filter(d -> d.weekday() == DayOfWeek.FRIDAY).findFirst().orElseThrow();
        assertThat(monday.booked()).isEqualTo(2);
        assertThat(friday.booked()).isEqualTo(1);
        assertThat(s.byWeekday().get(0).weekday()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void rankingExigeTresReservasYOrdenaPorFaltasYLuegoPorPeorTasa() {
        // Socio 1: 4 reservas, 3 faltas. Socio 2: 3 reservas, 3 faltas (peor tasa que el 1: 0% vs 25%).
        // Socio 3: 2 reservas, 2 faltas -> no entra (mínimo 3). Socio 4: 3 reservas, todas vino -> no entra.
        for (int i = 1; i <= 3; i++) {
            res(GAP, 1, i, false);
            res(GAP, 2, i, false);
            res(GAP, 4, i, true);
        }
        res(GAP, 1, 4, true);
        res(GAP, 3, 1, false);
        res(GAP, 3, 2, false);

        AttendanceSummaryResponse s = service.summary(GYM_ID, today.minusDays(10), to);

        assertThat(s.topAbsentees()).extracting("memberId").containsExactly(2L, 1L);
        assertThat(s.topAbsentees().get(1).missed()).isEqualTo(3);
        assertThat(s.topAbsentees().get(0).lastMissedDate()).isEqualTo(today.minusDays(1));
    }

    @Test
    void losQueMasVienenExigenTresReservasYOrdenanPorAsistenciasYLuegoPorTasa() {
        // Socio 1: 4 reservas, 4 asistencias. Socio 2: 5 reservas, 4 asistencias (misma cantidad, peor tasa).
        // Socio 3: 2 reservas, 2 asistencias -> no entra (mínimo 3). Socio 4: 3 reservas, ninguna -> no entra.
        for (int i = 1; i <= 4; i++) {
            res(GAP, 1, i, true);
            res(GAP, 2, i, true);
        }
        res(GAP, 2, 5, false);
        res(GAP, 3, 1, true);
        res(GAP, 3, 2, true);
        for (int i = 1; i <= 3; i++) {
            res(GAP, 4, i, false);
        }

        AttendanceSummaryResponse s = service.summary(GYM_ID, today.minusDays(10), to);

        assertThat(s.topAttendees()).extracting("memberId").containsExactly(1L, 2L);
        assertThat(s.topAttendees().get(0).attended()).isEqualTo(4);
    }

    @Test
    void losQueMasVienenSeLimitanALosDiez() {
        for (long m = 1; m <= 12; m++) {
            for (int i = 1; i <= 3; i++) {
                res(GAP, m, i, true);
            }
        }

        assertThat(service.summary(GYM_ID, from, to).topAttendees()).hasSize(10);
    }

    @Test
    void rankingSeLimitaALosDiezPrimeros() {
        for (long m = 1; m <= 12; m++) {
            for (int i = 1; i <= 3; i++) {
                res(GAP, m, i, false);
            }
        }

        assertThat(service.summary(GYM_ID, from, to).topAbsentees()).hasSize(10);
    }

    @Test
    void periodoAnteriorEsDelMismoLargoJustoAntes() {
        res(GAP, 1, 1, true); // período actual (últimos 7 días)
        res(GAP, 2, 8, true); // período anterior (días 8 a 14)
        res(GAP, 3, 9, false);
        res(GAP, 4, 20, true); // fuera de los dos

        AttendanceSummaryResponse s = service.summary(GYM_ID, from, to);

        assertThat(s.totals().booked()).isEqualTo(1);
        assertThat(s.previousTotals().booked()).isEqualTo(2);
        assertThat(s.previousTotals().attended()).isEqualTo(1);
    }

    @Test
    void periodoAnteriorExplicitoPermiteCompararMesesDeDistintoLargo() {
        res(GAP, 1, 1, true); // período actual
        res(GAP, 2, 25, true); // fuera de 7 + 7 días, pero dentro del anterior explícito de 30 días
        res(GAP, 3, 30, false);

        AttendanceSummaryResponse s = service.summary(GYM_ID, from, to, from.minusDays(30));

        assertThat(s.previousTotals().booked()).isEqualTo(2);
        assertThat(service.summary(GYM_ID, from, to).previousTotals().booked()).isZero();
    }

    @Test
    void periodoAnteriorExplicitoNoPuedeSerMuyLargoNiPosterior() {
        assertThatThrownBy(() -> service.summary(GYM_ID, from, to, from.minusDays(63)))
                .isInstanceOf(InvalidAttendanceException.class);
        assertThatThrownBy(() -> service.summary(GYM_ID, from, to, from))
                .isInstanceOf(InvalidAttendanceException.class);
    }

    @Test
    void rangoMayorA62DiasEsInvalido() {
        assertThatThrownBy(() -> service.summary(GYM_ID, today.minusDays(62), today))
                .isInstanceOf(InvalidAttendanceException.class);
        assertThat(service.summary(GYM_ID, today.minusDays(61), today)).isNotNull();
    }

    @Test
    void rangoInvertidoEsInvalido() {
        assertThatThrownBy(() -> service.summary(GYM_ID, today, today.minusDays(1)))
                .isInstanceOf(InvalidAttendanceException.class);
    }

    // --- lista de socios ---

    @Test
    void listaConBloqueDeOtroGymEsNotFound() {
        assertThatThrownBy(() -> service.members(GYM_ID, from, to, 999L, null, false, 0, 25))
                .isInstanceOf(GymBlockNotFoundException.class);
    }

    @Test
    void listaFiltraPorBloqueDiaYSoloFaltas() {
        res(GAP, 1, 1, true);
        res(GAP, 2, 1, false);
        res(CROSSFIT, 3, 2, false);
        res(CROSSFIT, 4, 2, true);

        var byBlock = service.members(GYM_ID, from, to, GAP, null, false, 0, 25);
        var byDay = service.members(GYM_ID, from, to, null, DayOfWeek.FRIDAY, false, 0, 25);
        var onlyMissed = service.members(GYM_ID, from, to, null, null, true, 0, 25);

        assertThat(byBlock.items()).extracting("memberId").containsExactlyInAnyOrder(1L, 2L);
        assertThat(byDay.items()).extracting("memberId").containsExactlyInAnyOrder(3L, 4L);
        assertThat(onlyMissed.items()).extracting("memberId").containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void listaOrdenaPorFaltasYTasaYPagina() {
        // Socio 1: 1 falta de 2. Socio 2: 2 faltas de 2. Socio 3: 1 falta de 1 (tasa 0%).
        res(GAP, 1, 1, false);
        res(GAP, 1, 2, true);
        res(GAP, 2, 1, false);
        res(GAP, 2, 2, false);
        res(GAP, 3, 1, false);

        var all = service.members(GYM_ID, from, to, null, null, false, 0, 25);
        assertThat(all.items()).extracting("memberId").containsExactly(2L, 3L, 1L);

        var first = service.members(GYM_ID, from, to, null, null, false, 0, 2);
        var second = service.members(GYM_ID, from, to, null, null, false, 1, 2);
        assertThat(first.items()).hasSize(2);
        assertThat(first.hasNext()).isTrue();
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(second.items()).extracting("memberId").containsExactly(1L);
        assertThat(second.hasNext()).isFalse();
    }

    @Test
    void tamanoDePaginaTieneTopeDe50() {
        for (long m = 1; m <= 60; m++) {
            res(GAP, m, 1, false);
        }

        var page = service.members(GYM_ID, from, to, null, null, false, 0, 10_000);

        assertThat(page.items()).hasSize(50);
        assertThat(page.hasNext()).isTrue();
    }

    @Test
    void paginaMasAllaDelFinalEsVacia() {
        res(GAP, 1, 1, false);

        var page = service.members(GYM_ID, from, to, null, null, false, 5, 25);

        assertThat(page.items()).isEmpty();
        assertThat(page.hasNext()).isFalse();
    }

    // --- detalle de un socio ---

    @Test
    void detalleDeSocioDeOtroGymEsNotFound() {
        when(appUserRepository.findByIdAndGymId(5L, GYM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.memberDetail(GYM_ID, 5L, from, to))
                .isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    void detalleListaCadaReservaYMarcaEditableSoloDentroDeLosSieteDias() {
        when(appUserRepository.findByIdAndGymId(1L, GYM_ID)).thenReturn(Optional.of(user(1L)));
        Reservation recent = res(GAP, 1, 1, false);
        Reservation old = res(GAP, 1, 30, true);
        when(reservationRepository.findByMemberIdAndClassDateBetween(anyLong(), any(), any()))
                .thenReturn(List.of(recent, old));

        MemberAttendanceDetail detail = service.memberDetail(GYM_ID, 1L, today.minusDays(40), to);

        assertThat(detail.rows()).hasSize(2);
        assertThat(detail.rows().get(0).reservationId()).isEqualTo(recent.getId()); // más reciente primero
        assertThat(detail.rows().get(0).editable()).isTrue();
        assertThat(detail.rows().get(0).attended()).isFalse();
        assertThat(detail.rows().get(1).editable()).isFalse();
        assertThat(detail.rows().get(1).checkinMethod()).isEqualTo(CheckinMethod.QR);
        assertThat(detail.member().booked()).isEqualTo(2);
        assertThat(detail.member().missed()).isEqualTo(1);
    }
}
