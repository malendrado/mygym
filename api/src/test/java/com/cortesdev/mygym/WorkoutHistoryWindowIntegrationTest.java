package com.cortesdev.mygym;

import static org.assertj.core.api.Assertions.assertThat;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.ExerciseLogEntry;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.MemberWorkoutLog;
import com.cortesdev.mygym.models.PlanDay;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.WorkoutPlan;
import com.cortesdev.mygym.models.dto.PendingWorkoutResponse;
import com.cortesdev.mygym.models.dto.WorkoutLogPageResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.MemberWorkoutLogRepository;
import com.cortesdev.mygym.repositories.PlanDayRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.repositories.WorkoutPlanRepository;
import com.cortesdev.mygym.services.WorkoutService;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Historial de rutina por ventanas contra Postgres real: las consultas JPQL nuevas (ancla, ventana, última del plan,
 * clase pendiente) y el índice parcial de V48. Lo que los mocks no pueden demostrar.
 */
@SpringBootTest
@Transactional
class WorkoutHistoryWindowIntegrationTest {

    private static final LocalDate BASE = LocalDate.of(2026, 10, 14);

    @Autowired private WorkoutService workoutService;
    @Autowired private GymRepository gymRepository;
    @Autowired private GymBlockRepository gymBlockRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private WorkoutPlanRepository workoutPlanRepository;
    @Autowired private PlanDayRepository planDayRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private MemberWorkoutLogRepository logRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    private Long gymId;
    private Long blockId;
    private Long memberId;
    private PlanDay day1;
    private PlanDay day2;

    @BeforeEach
    void setUp() {
        Gym gym = gymRepository.save(Gym.builder()
                .name("Gym historial").slug("gym-historial-" + System.nanoTime()).maxUsers(10).active(true)
                .googleLoginEnabled(true).bookingWindowMinutes(0).cancellationWindowMinutes(0).build());
        gymId = gym.getId();
        blockId = gymBlockRepository.save(GymBlock.builder()
                        .gymId(gymId).label("Clase").dayOfWeek(BASE.getDayOfWeek()).startTime(LocalTime.of(9, 0))
                        .endTime(LocalTime.of(10, 0)).capacity(10).active(true).build())
                .getId();
        memberId = appUserRepository.save(AppUser.builder()
                        .email("socio-" + System.nanoTime() + "@test.com").name("Socio").role(Role.MEMBER)
                        .gymId(gymId).active(true).build())
                .getId();
        WorkoutPlan plan = workoutPlanRepository.save(
                WorkoutPlan.builder().memberId(memberId).name("Rutina").active(true).build());
        day1 = planDayRepository.save(PlanDay.builder().workoutPlanId(plan.getId()).orderIndex(1).title("Día 1").build());
        day2 = planDayRepository.save(PlanDay.builder().workoutPlanId(plan.getId()).orderIndex(2).title("Día 2").build());
    }

    /** Una clase con asistencia en `daysAgo` días antes de BASE. withLog=false = asistió pero no anotó. */
    private Reservation attended(int daysAgo, boolean withLog) {
        Reservation r = reservationRepository.save(Reservation.builder()
                .gymBlockId(blockId).memberId(memberId).classDate(BASE.minusDays(daysAgo))
                .status(ReservationStatus.BOOKED).checkedInAt(Instant.now()).build());
        if (withLog) {
            logRepository.save(MemberWorkoutLog.builder()
                    .reservationId(r.getId()).suggestedPlanDayId(day1.getId()).planDayId(day2.getId())
                    .exercisesLog(List.of(new ExerciseLogEntry("Press banca", "40 kg")))
                    .build());
        }
        return r;
    }

    private List<LocalDate> dates(WorkoutLogPageResponse page) {
        return page.logs().stream().map(l -> l.classDate()).toList();
    }

    @Test
    void theFirstWindowHoldsTheLastSevenDaysFromTheNewestClassAndPointsToTheNextOne() {
        attended(0, true);  // 14 oct
        attended(2, true);  // 12 oct
        attended(6, true);  // 8 oct  (todavía dentro de los 7 días de la ventana)
        attended(7, true);  // 7 oct  (primer registro fuera de la ventana)
        attended(20, true); // 24 sep
        entityManager.flush();

        WorkoutLogPageResponse page = workoutService.getMyLogHistory(memberId, null, null);

        assertThat(dates(page)).containsExactly(BASE, BASE.minusDays(2), BASE.minusDays(6));
        assertThat(page.nextBefore()).isEqualTo(BASE.minusDays(6));
        assertThat(page.logs().get(0).exercises()).extracting(ExerciseLogEntry::exercise).containsExactly("Press banca");
        assertThat(page.logs().get(0).planDayTitle()).isEqualTo("Día 2");
    }

    @Test
    void followingTheCursorWalksBackThroughEveryRecordWithoutRepeatsOrGaps() {
        attended(0, true);
        attended(2, true);
        attended(6, true);
        attended(7, true);
        attended(20, true);
        attended(40, true);
        entityManager.flush();

        List<LocalDate> seen = new ArrayList<>();
        LocalDate cursor = null;
        int pages = 0;
        do {
            WorkoutLogPageResponse page = workoutService.getMyLogHistory(memberId, cursor, null);
            seen.addAll(dates(page));
            cursor = page.nextBefore();
            pages++;
        } while (cursor != null && pages < 10);

        assertThat(pages).isEqualTo(4);
        assertThat(seen).containsExactly(
                BASE, BASE.minusDays(2), BASE.minusDays(6),
                BASE.minusDays(7),
                BASE.minusDays(20),
                BASE.minusDays(40));
    }

    @Test
    void aMemberWhoStoppedTrainingWeeksAgoStillGetsAFullFirstWindow() {
        attended(30, true);
        attended(31, true);
        entityManager.flush();

        WorkoutLogPageResponse page = workoutService.getMyLogHistory(memberId, null, null);

        assertThat(dates(page)).containsExactly(BASE.minusDays(30), BASE.minusDays(31));
        assertThat(page.nextBefore()).isNull();
    }

    @Test
    void classesAttendedButNeverLoggedDoNotShowUpInTheHistory() {
        attended(0, false);
        attended(1, true);
        entityManager.flush();

        WorkoutLogPageResponse page = workoutService.getMyLogHistory(memberId, null, null);

        assertThat(dates(page)).containsExactly(BASE.minusDays(1));
    }

    @Test
    void aMemberWithNoLogsGetsAnEmptyPage() {
        WorkoutLogPageResponse page = workoutService.getMyLogHistory(memberId, null, null);

        assertThat(page.logs()).isEmpty();
        assertThat(page.nextBefore()).isNull();
    }

    @Test
    void theCallerCanWidenTheWindowUpTo93Days() {
        attended(0, true);
        attended(40, true);
        attended(80, true);
        entityManager.flush();

        WorkoutLogPageResponse page = workoutService.getMyLogHistory(memberId, null, 5000);

        assertThat(dates(page)).containsExactly(BASE, BASE.minusDays(40), BASE.minusDays(80));
        assertThat(page.nextBefore()).isNull();
    }

    @Test
    void thePendingClassIsTheNewestAttendedOneWithoutALogAndNothingElseIsLoaded() {
        attended(0, false); // la más reciente sin registro: es la pendiente
        attended(3, false);
        attended(5, true);
        entityManager.flush();

        PendingWorkoutResponse pending = workoutService.getPendingWorkout(memberId);

        assertThat(pending.status()).isEqualTo("READY");
        assertThat(pending.classDate()).isEqualTo(BASE);
        // El último registro (clase de hace 5 días) hizo el Día 2: lo sugerido ahora es el siguiente del ciclo (Día 1).
        assertThat(pending.suggestedPlanDayId()).isEqualTo(day1.getId());
    }

    @Test
    void noPendingClassWhenEveryAttendedClassHasItsLog() {
        attended(0, true);
        entityManager.flush();

        assertThat(workoutService.getPendingWorkout(memberId).status()).isEqualTo("NO_PENDING");
    }

    @Test
    void theNewestLogOfThePlanIsFoundByClassDateNotByInsertionOrder() {
        attended(10, true);
        attended(1, true); // fecha más reciente
        attended(5, true);
        entityManager.flush();

        var newest = logRepository.findNewestOfPlan(
                memberId, List.of(day1.getId(), day2.getId()), PageRequest.of(0, 1));

        assertThat(newest).hasSize(1);
        Reservation r = reservationRepository.findById(newest.get(0).getReservationId()).orElseThrow();
        assertThat(r.getClassDate()).isEqualTo(BASE.minusDays(1));
    }

    @Test
    void theHistoryQueriesUseTheCheckedInPartialIndex() {
        attended(0, true);
        entityManager.flush();

        // Con la tabla casi vacía el planificador prefiere un recorrido secuencial: se lo prohíbe para ver si el índice
        // parcial sirve para esta consulta (misma forma que newestClassDateBefore).
        List<String> plan = jdbc.execute((java.sql.Connection c) -> {
            try (var st = c.createStatement()) {
                st.execute("set local enable_seqscan = off");
                List<String> lines = new ArrayList<>();
                try (var rs = st.executeQuery(
                        "explain select max(r.class_date) from member_workout_log l join reservation r on r.id = l.reservation_id"
                                + " where r.member_id = " + memberId + " and r.checked_in_at is not null and r.class_date < '2030-01-01'")) {
                    while (rs.next()) {
                        lines.add(rs.getString(1));
                    }
                }
                return lines;
            }
        });

        assertThat(String.join("\n", plan)).contains("idx_reservation_member_checkedin_date");
    }

    // ---- paquete 3 de la auditoría 2026-10-17: consultas livianas del tablero y de las reservas ----

    @Test
    void theBoardAsksOnceForTheDayOfTheNewestLogOfEachPlanWithoutLoadingTheExercises() {
        attended(10, true); // hizo el día 2, hace 10 días
        Reservation newest = attended(3, false);
        logRepository.save(MemberWorkoutLog.builder()
                .reservationId(newest.getId()).suggestedPlanDayId(day2.getId()).planDayId(day1.getId())
                .exercisesLog(List.of(new ExerciseLogEntry("Sentadilla", "60 kg"))).build());
        // Otro socio con su propio plan: no se mezcla.
        AppUser other = appUserRepository.save(AppUser.builder()
                .email("otro-" + System.nanoTime() + "@test.com").name("Otro").role(Role.MEMBER).gymId(gymId).active(true).build());
        WorkoutPlan otherPlan = workoutPlanRepository.save(WorkoutPlan.builder().memberId(other.getId()).name("R2").active(true).build());
        planDayRepository.save(PlanDay.builder().workoutPlanId(otherPlan.getId()).orderIndex(1).title("A").build());
        entityManager.flush();

        List<MemberWorkoutLogRepository.LatestPlanLog> rows = logRepository.findLatestPlanDayByPlan(
                List.of(day1.getWorkoutPlanId(), otherPlan.getId()));

        // Solo el plan con registros; y de sus dos registros, el de la clase más reciente (hace 3 días: hizo el día 1).
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getPlanId()).isEqualTo(day1.getWorkoutPlanId());
        assertThat(rows.get(0).getPlanDayId()).isEqualTo(day1.getId());
    }

    @Test
    void aFreeTextNewestLogMeansThePlanRestartsAtDayOneNotThePreviousDay() {
        attended(10, true);
        Reservation newest = attended(2, false);
        logRepository.save(MemberWorkoutLog.builder()
                .reservationId(newest.getId()).suggestedPlanDayId(day1.getId()).planDayId(null).freeTextLabel("Cardio libre")
                .exercisesLog(List.of()).build());
        entityManager.flush();

        List<MemberWorkoutLogRepository.LatestPlanLog> rows = logRepository.findLatestPlanDayByPlan(List.of(day1.getWorkoutPlanId()));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getPlanDayId()).isNull();
    }

    @Test
    void theLightGymViewCarriesTheBookingRulesButNotTheHeavyColumns() {
        Gym full = gymRepository.findById(gymId).orElseThrow();
        full.setLogoSvg("<svg>logo</svg>");
        full.setBookingWindowMinutes(45);
        full.setCancellationWindowMinutes(20);
        full.setWaitlistHeadStartMinutes(15);
        full.setShowAttendeesToMembers(true);
        full.setFixedMembersCurrentWeekOnly(true);
        gymRepository.save(full);
        entityManager.flush();
        entityManager.clear();

        Gym light = gymRepository.findSettingsViewById(gymId).orElseThrow();

        assertThat(light.getName()).isEqualTo("Gym historial");
        assertThat(light.isActive()).isTrue();
        assertThat(light.getBookingWindowMinutes()).isEqualTo(45);
        assertThat(light.getCancellationWindowMinutes()).isEqualTo(20);
        assertThat(light.getWaitlistHeadStartMinutes()).isEqualTo(15);
        assertThat(light.isShowAttendeesToMembers()).isTrue();
        assertThat(light.isFixedMembersCurrentWeekOnly()).isTrue();
        assertThat(light.getLogoSvg()).isNull(); // el logo no se trae
        assertThat(gymRepository.findSettingsViewById(-1L)).isEmpty();
    }

    @Test
    void theForeignKeyIndexesOfV51ExistAndTheDuplicatesOfV49AreGone() {
        List<String> names = jdbc.queryForList("select indexname from pg_indexes where schemaname = 'public'", String.class);

        assertThat(names).contains(
                "idx_member_workout_log_plan_day", "idx_manual_payment_plan", "idx_manual_payment_registered_by",
                "idx_payment_plan", "idx_app_user_plan");
        assertThat(names).doesNotContain("idx_payment_flow_token", "idx_reservation_cancelled_by_closure");
        assertThat(names).contains("uk_payment_flow_token", "idx_reservation_cancelled_by_closure_id");
    }
}
