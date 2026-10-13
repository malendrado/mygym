package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.CheckinMethod;
import com.cortesdev.mygym.models.ExerciseLogEntry;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.MemberWorkoutLog;
import com.cortesdev.mygym.models.PlanDay;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.WorkoutPlan;
import com.cortesdev.mygym.models.dto.ClassBoardResponse;
import com.cortesdev.mygym.models.dto.ClassBoardResponse.BoardAttendee;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.MemberWorkoutLogRepository;
import com.cortesdev.mygym.repositories.PlanDayRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.repositories.WorkoutPlanRepository;
import com.cortesdev.mygym.services.exception.GymNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Tablero "En clase" (Mockito, sin DB): qué clases aparecen, qué muestra cada tarjeta y que las consultas sean un
 *  número fijo (sin una consulta por alumno). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClassBoardServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Santiago");
    private static final Long GYM_ID = 7L;

    @Mock private GymRepository gymRepository;
    @Mock private GymBlockRepository gymBlockRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private WorkoutPlanRepository workoutPlanRepository;
    @Mock private PlanDayRepository planDayRepository;
    @Mock private MemberWorkoutLogRepository memberWorkoutLogRepository;
    @Mock private GymClosureService gymClosureService;

    private ClassBoardService service;
    private ZonedDateTime now;
    private LocalDate today;

    private final List<GymBlock> blocks = new ArrayList<>();
    private final List<Reservation> reservations = new ArrayList<>(); // las del tablero (BOOKED)
    private final List<Reservation> attended = new ArrayList<>(); // asistencias recientes
    private final List<AppUser> users = new ArrayList<>();
    private final List<MemberWorkoutLog> logs = new ArrayList<>();
    private final List<PlanDay> days = new ArrayList<>();
    private final List<WorkoutPlan> plans = new ArrayList<>();
    private Set<Long> closedBlocks = Set.of();
    private List<Long> priorBookers = List.of();
    private long nextId = 1000;

    @BeforeEach
    void setUp() {
        now = ZonedDateTime.now(ZONE);
        today = now.toLocalDate();
        // Los casos usan horas relativas a "ahora": no corren pegados a la medianoche.
        assumeTrue(now.toLocalTime().isAfter(LocalTime.of(0, 40)) && now.toLocalTime().isBefore(LocalTime.of(21, 0)));

        service = new ClassBoardService(
                gymRepository,
                gymBlockRepository,
                reservationRepository,
                appUserRepository,
                workoutPlanRepository,
                planDayRepository,
                memberWorkoutLogRepository,
                gymClosureService,
                new WorkoutService(
                        workoutPlanRepository, planDayRepository, memberWorkoutLogRepository, reservationRepository, appUserRepository));

        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(Gym.builder().id(GYM_ID).build()));
        when(gymBlockRepository.findByGymId(GYM_ID)).thenAnswer(inv -> List.copyOf(blocks));
        when(gymClosureService.scheduleFor(any(), any(), any()))
                .thenAnswer(inv -> new GymClosureService.ClosureSchedule(
                        Set.of(), closedBlocks.stream().map(id -> id + "|" + today).collect(java.util.stream.Collectors.toSet())));
        when(reservationRepository.findByGymBlockIdInAndClassDateBetweenAndStatus(any(), any(), any(), any()))
                .thenAnswer(inv -> List.copyOf(reservations));
        when(appUserRepository.findAllById(any())).thenAnswer(inv -> {
            Iterable<Long> ids = inv.getArgument(0);
            List<AppUser> out = new ArrayList<>();
            ids.forEach(id -> users.stream().filter(u -> u.getId().equals(id)).findFirst().ifPresent(out::add));
            return out;
        });
        when(workoutPlanRepository.findByMemberIdInAndActiveTrue(any())).thenAnswer(inv -> List.copyOf(plans));
        when(planDayRepository.findByWorkoutPlanIdInOrderByOrderIndexAsc(any())).thenAnswer(inv -> List.copyOf(days));
        when(planDayRepository.findAllById(any())).thenAnswer(inv -> List.copyOf(days));
        when(memberWorkoutLogRepository.findBySuggestedPlanDayIdIn(any())).thenAnswer(inv -> List.copyOf(logs));
        when(memberWorkoutLogRepository.findByReservationIdIn(any())).thenAnswer(inv -> List.copyOf(logs));
        when(reservationRepository.findAllById(any())).thenAnswer(inv -> {
            Iterable<Long> ids = inv.getArgument(0);
            List<Reservation> out = new ArrayList<>();
            List<Reservation> all = new ArrayList<>(attended);
            all.addAll(reservations);
            ids.forEach(id -> all.stream().filter(r -> r.getId().equals(id)).findFirst().ifPresent(out::add));
            return out;
        });
        when(reservationRepository.findByMemberIdInAndCheckedInAtIsNotNullAndClassDateGreaterThanEqual(any(), any()))
                .thenAnswer(inv -> List.copyOf(attended));
        when(reservationRepository.findMemberIdsWithBookingBefore(any(), any())).thenAnswer(inv -> priorBookers);
    }

    // ---- helpers ----

    private GymBlock block(long id, String label, LocalTime start, LocalTime end) {
        GymBlock b = GymBlock.builder()
                .id(id)
                .gymId(GYM_ID)
                .label(label)
                .dayOfWeek(today.getDayOfWeek())
                .startTime(start)
                .endTime(end)
                .capacity(12)
                .active(true)
                .build();
        blocks.add(b);
        return b;
    }

    private GymBlock liveBlock(long id, String label) {
        LocalTime t = now.toLocalTime().withSecond(0).withNano(0);
        return block(id, label, t.minusMinutes(10), t.plusMinutes(50));
    }

    private AppUser member(long id, String name) {
        AppUser u = AppUser.builder().id(id).gymId(GYM_ID).role(Role.MEMBER).name(name).phone("+5691234" + id).build();
        users.add(u);
        return u;
    }

    private Reservation booked(GymBlock b, AppUser m, boolean checkedIn) {
        Reservation r = Reservation.builder()
                .id(nextId++)
                .gymBlockId(b.getId())
                .memberId(m.getId())
                .classDate(today)
                .status(ReservationStatus.BOOKED)
                .checkedInAt(checkedIn ? Instant.now() : null)
                .checkinMethod(checkedIn ? CheckinMethod.QR : null)
                .build();
        reservations.add(r);
        return r;
    }

    private Reservation attendedBefore(AppUser m, int daysAgo) {
        Reservation r = Reservation.builder()
                .id(nextId++)
                .gymBlockId(1L)
                .memberId(m.getId())
                .classDate(today.minusDays(daysAgo))
                .status(ReservationStatus.BOOKED)
                .checkedInAt(Instant.now())
                .build();
        attended.add(r);
        return r;
    }

    private void planFor(AppUser m, long planId, String... titles) {
        plans.add(WorkoutPlan.builder().id(planId).memberId(m.getId()).name("Fuerza").active(true).build());
        int order = 1;
        for (String t : titles) {
            days.add(PlanDay.builder().id(planId * 10 + order).workoutPlanId(planId).orderIndex(order).title(t).build());
            order++;
        }
    }

    private MemberWorkoutLog logFor(Reservation r, long suggestedDayId, Long doneDayId, String free, Long by, ExerciseLogEntry... ex) {
        MemberWorkoutLog l = MemberWorkoutLog.builder()
                .id(nextId++)
                .reservationId(r.getId())
                .suggestedPlanDayId(suggestedDayId)
                .planDayId(doneDayId)
                .freeTextLabel(free)
                .exercisesLog(List.of(ex))
                .loggedByUserId(by)
                .createdAt(Instant.now())
                .build();
        logs.add(l);
        return l;
    }

    private BoardAttendee attendee(ClassBoardResponse board, long memberId) {
        return board.classes().get(0).attendees().stream()
                .filter(a -> a.memberId().equals(memberId))
                .findFirst()
                .orElseThrow();
    }

    // ---- qué clases aparecen ----

    @Test
    void muestraTodasLasClasesEnCursoYLaSiguiente() {
        liveBlock(1, "GAP");
        liveBlock(2, "CrossFit"); // simultánea: las dos aparecen
        LocalTime later = now.toLocalTime().withSecond(0).withNano(0).plusMinutes(90);
        block(3, "Yoga", later, later.plusMinutes(60));

        ClassBoardResponse board = service.board(GYM_ID);

        assertThat(board.classes()).extracting("status").containsExactly("LIVE", "LIVE", "NEXT");
        assertThat(board.classes()).extracting("label").containsExactly("GAP", "CrossFit", "Yoga");
    }

    @Test
    void laSiguienteSonTodasLasQueEmpiezanALaMismaHora() {
        LocalTime later = now.toLocalTime().withSecond(0).withNano(0).plusMinutes(90);
        block(1, "GAP", later, later.plusMinutes(60));
        block(2, "CrossFit", later, later.plusMinutes(60));
        block(3, "Yoga", later.plusMinutes(60), later.plusMinutes(120)); // más tarde: no es "la siguiente"

        ClassBoardResponse board = service.board(GYM_ID);

        assertThat(board.classes()).extracting("label").containsExactly("GAP", "CrossFit");
        assertThat(board.classes()).extracting("status").containsOnly("NEXT");
    }

    @Test
    void unaClaseCerradaPorEmergenciaNoAparece() {
        liveBlock(1, "GAP");
        closedBlocks = Set.of(1L);

        // Hoy está cerrada: no aparece como "en curso" ni como "siguiente de hoy"; la siguiente es la de la semana próxima.
        ClassBoardResponse board = service.board(GYM_ID);
        assertThat(board.classes()).noneMatch(c -> c.status().equals("LIVE"));
        assertThat(board.classes()).allMatch(c -> c.classDate().isAfter(today));
    }

    @Test
    void sinClasesDevuelveListaVacia() {
        assertThat(service.board(GYM_ID).classes()).isEmpty();
    }

    @Test
    void gymInexistenteEsNotFound() {
        when(gymRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.board(99L)).isInstanceOf(GymNotFoundException.class);
    }

    @Test
    void bloqueInactivoNoAparece() {
        liveBlock(1, "GAP").setActive(false);

        assertThat(service.board(GYM_ID).classes()).isEmpty();
    }

    // ---- la tarjeta de cada alumno ----

    @Test
    void queLeTocaYLoUltimoQueHizo() {
        GymBlock gap = liveBlock(1, "GAP");
        AppUser camila = member(21, "Camila");
        planFor(camila, 5, "Pecho", "Espalda", "Piernas");
        booked(gap, camila, true);
        Reservation lastTime = attendedBefore(camila, 3);
        // El último día que hizo fue el Día 1 (id 51) y aceptó lo sugerido -> hoy le toca el Día 2.
        logFor(lastTime, 51L, 51L, null, null, new ExerciseLogEntry("Press banca", "4x8"), new ExerciseLogEntry("Fondos", null));

        BoardAttendee a = attendee(service.board(GYM_ID), 21);

        assertThat(a.hasPlan()).isTrue();
        assertThat(a.nextDay().title()).isEqualTo("Espalda");
        assertThat(a.nextDay().order()).isEqualTo(2);
        assertThat(a.nextDay().total()).isEqualTo(3);
        assertThat(a.lastLog().dayTitle()).isEqualTo("Pecho");
        assertThat(a.lastLog().deviated()).isFalse();
        assertThat(a.lastLog().exercises()).hasSize(2);
        assertThat(a.unloggedLast()).isFalse();
    }

    @Test
    void siSeDesvioLoDice() {
        GymBlock gap = liveBlock(1, "GAP");
        AppUser camila = member(21, "Camila");
        planFor(camila, 5, "Pecho", "Espalda");
        booked(gap, camila, true);
        Reservation lastTime = attendedBefore(camila, 3);
        logFor(lastTime, 51L, null, "Hice cardio", null, new ExerciseLogEntry("Trote", "20 min"));

        BoardAttendee a = attendee(service.board(GYM_ID), 21);

        assertThat(a.lastLog().deviated()).isTrue();
        assertThat(a.lastLog().doneLabel()).isEqualTo("Hice cardio");
        // Texto libre reinicia el ciclo: hoy le toca el Día 1.
        assertThat(a.nextDay().title()).isEqualTo("Pecho");
    }

    @Test
    void sinPlanNoTieneQueLeToca() {
        GymBlock gap = liveBlock(1, "GAP");
        AppUser tomas = member(22, "Tomás");
        booked(gap, tomas, false);

        BoardAttendee a = attendee(service.board(GYM_ID), 22);

        assertThat(a.hasPlan()).isFalse();
        assertThat(a.nextDay()).isNull();
        assertThat(a.lastLog()).isNull();
        assertThat(a.checkedInAt()).isNull();
    }

    @Test
    void noAnotoLaClaseAnteriorSiVinoYNoHayRegistro() {
        GymBlock gap = liveBlock(1, "GAP");
        AppUser ana = member(23, "Ana");
        planFor(ana, 6, "Día 1");
        booked(gap, ana, false);
        attendedBefore(ana, 2); // vino y no anotó nada

        assertThat(attendee(service.board(GYM_ID), 23).unloggedLast()).isTrue();
    }

    @Test
    void primeraClaseSiNuncaHabiaReservadoAntes() {
        GymBlock gap = liveBlock(1, "GAP");
        AppUser nuevo = member(24, "Nuevo");
        AppUser veterano = member(25, "Veterano");
        booked(gap, nuevo, false);
        booked(gap, veterano, false);
        priorBookers = List.of(25L);

        ClassBoardResponse board = service.board(GYM_ID);

        assertThat(attendee(board, 24).firstClass()).isTrue();
        assertThat(attendee(board, 25).firstClass()).isFalse();
    }

    @Test
    void elRegistroDeEstaClaseSeMuestraConQuienLoAnoto() {
        GymBlock gap = liveBlock(1, "GAP");
        AppUser camila = member(21, "Camila");
        AppUser profe = AppUser.builder().id(90L).gymId(GYM_ID).role(Role.PROFESOR).name("Profe Juan").build();
        users.add(profe);
        planFor(camila, 5, "Pecho", "Espalda");
        Reservation today = booked(gap, camila, true);
        logFor(today, 51L, 51L, null, 90L, new ExerciseLogEntry("Press banca", "60 kg"));

        BoardAttendee a = attendee(service.board(GYM_ID), 21);

        assertThat(a.thisClassLog()).isNotNull();
        assertThat(a.thisClassLog().loggedByName()).isEqualTo("Profe Juan");
        assertThat(a.thisClassLog().planDayTitle()).isEqualTo("Pecho");
    }

    @Test
    void alumnosOrdenadosPorNombre() {
        GymBlock gap = liveBlock(1, "GAP");
        booked(gap, member(31, "Zoe"), false);
        booked(gap, member(32, "Ana"), false);
        booked(gap, member(33, "Mario"), false);

        assertThat(service.board(GYM_ID).classes().get(0).attendees())
                .extracting("name")
                .containsExactly("Ana", "Mario", "Zoe");
    }

    // ---- sin N+1 ----

    @Test
    void lasConsultasSonUnNumeroFijoSinImportarCuantosAlumnos() {
        GymBlock gap = liveBlock(1, "GAP");
        for (long i = 1; i <= 25; i++) {
            AppUser m = member(100 + i, "Alumno " + i);
            planFor(m, 200 + i, "Día 1", "Día 2");
            booked(gap, m, i % 2 == 0);
            Reservation before = attendedBefore(m, 4);
            logFor(before, (200 + i) * 10 + 1, (200 + i) * 10 + 1, null, null, new ExerciseLogEntry("Sentadilla", "4x8"));
        }

        ClassBoardResponse board = service.board(GYM_ID);

        assertThat(board.classes().get(0).attendees()).hasSize(25);
        // Cada tipo de consulta se hace UNA vez para toda la clase (nunca una por alumno).
        verify(workoutPlanRepository, times(1)).findByMemberIdInAndActiveTrue(any());
        verify(planDayRepository, times(1)).findByWorkoutPlanIdInOrderByOrderIndexAsc(any());
        verify(memberWorkoutLogRepository, times(1)).findBySuggestedPlanDayIdIn(any());
        verify(memberWorkoutLogRepository, times(1)).findByReservationIdIn(any());
        verify(reservationRepository, times(1))
                .findByMemberIdInAndCheckedInAtIsNotNullAndClassDateGreaterThanEqual(any(), any());
        verify(reservationRepository, times(1)).findMemberIdsWithBookingBefore(any(), any());
    }
}
