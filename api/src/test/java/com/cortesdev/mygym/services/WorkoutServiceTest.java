package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.MemberWorkoutLog;
import com.cortesdev.mygym.models.PlanDay;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.WorkoutPlan;
import com.cortesdev.mygym.models.dto.MemberWorkoutLogResponse;
import com.cortesdev.mygym.models.dto.PendingWorkoutResponse;
import com.cortesdev.mygym.models.dto.WorkoutLogSaveRequest;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.MemberWorkoutLogRepository;
import com.cortesdev.mygym.repositories.PlanDayRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.repositories.WorkoutPlanRepository;
import com.cortesdev.mygym.services.exception.InvalidWorkoutLogException;
import com.cortesdev.mygym.services.exception.WorkoutLogNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unidades puras (Mockito, sin Spring context ni DB) para la lógica de ciclo de "Memoria Viva" —
 * ver WorkoutService.getNextPlanDay/findPendingReservation y el refinamiento cerrado en la
 * memoria de proyecto mygym_memoria_viva_design. No requiere las env vars de Supabase (a
 * diferencia de los *ControllerTest existentes, que sí son @SpringBootTest contra la DB real).
 */
@ExtendWith(MockitoExtension.class)
class WorkoutServiceTest {

    private static final Long GYM_ID = 1L;
    private static final Long MEMBER_ID = 5L;
    private static final Long PLAN_ID = 10L;

    @Mock private WorkoutPlanRepository workoutPlanRepository;
    @Mock private PlanDayRepository planDayRepository;
    @Mock private MemberWorkoutLogRepository memberWorkoutLogRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private AppUserRepository appUserRepository;

    private WorkoutService workoutService;

    private PlanDay day1;
    private PlanDay day2;
    private PlanDay day3;
    private WorkoutPlan plan;

    @BeforeEach
    void setUp() {
        workoutService = new WorkoutService(
                workoutPlanRepository, planDayRepository, memberWorkoutLogRepository, reservationRepository, appUserRepository);

        day1 = PlanDay.builder().id(100L).workoutPlanId(PLAN_ID).orderIndex(1).title("Día 1: Pecho").build();
        day2 = PlanDay.builder().id(101L).workoutPlanId(PLAN_ID).orderIndex(2).title("Día 2: Espalda").build();
        day3 = PlanDay.builder().id(102L).workoutPlanId(PLAN_ID).orderIndex(3).title("Día 3: Piernas").build();
        plan = WorkoutPlan.builder().id(PLAN_ID).memberId(MEMBER_ID).name("Rutina de fuerza").active(true).build();
    }

    private Reservation reservation(Long id, LocalDate classDate) {
        return Reservation.builder()
                .id(id)
                .memberId(MEMBER_ID)
                .gymBlockId(999L)
                .classDate(classDate)
                .status(ReservationStatus.BOOKED)
                .checkedInAt(Instant.now())
                .build();
    }

    // ---- getPendingWorkout: 3 estados vacíos ----

    @Test
    void getPendingWorkout_sinPlanActivo_devuelveNoPlan() {
        when(workoutPlanRepository.findByMemberIdAndActiveTrue(MEMBER_ID)).thenReturn(Optional.empty());

        PendingWorkoutResponse response = workoutService.getPendingWorkout(MEMBER_ID);

        assertThat(response.status()).isEqualTo("NO_PLAN");
        assertThat(response.reservationId()).isNull();
        assertThat(response.plan()).isNull();
    }

    @Test
    void getPendingWorkout_sinReservasConCheckIn_devuelveNoPending() {
        when(workoutPlanRepository.findByMemberIdAndActiveTrue(MEMBER_ID)).thenReturn(Optional.of(plan));
        when(planDayRepository.findByWorkoutPlanIdOrderByOrderIndexAsc(PLAN_ID)).thenReturn(List.of(day1, day2, day3));
        when(reservationRepository.findByMemberIdAndCheckedInAtIsNotNullOrderByClassDateDesc(MEMBER_ID))
                .thenReturn(List.of());

        PendingWorkoutResponse response = workoutService.getPendingWorkout(MEMBER_ID);

        assertThat(response.status()).isEqualTo("NO_PENDING");
        assertThat(response.plan()).isNotNull();
        assertThat(response.plan().days()).hasSize(3);
    }

    @Test
    void getPendingWorkout_todasLasReservasConCheckInYaTienenLog_devuelveNoPending() {
        Reservation res1 = reservation(201L, LocalDate.of(2026, 10, 1));
        when(workoutPlanRepository.findByMemberIdAndActiveTrue(MEMBER_ID)).thenReturn(Optional.of(plan));
        when(planDayRepository.findByWorkoutPlanIdOrderByOrderIndexAsc(PLAN_ID)).thenReturn(List.of(day1, day2, day3));
        when(reservationRepository.findByMemberIdAndCheckedInAtIsNotNullOrderByClassDateDesc(MEMBER_ID))
                .thenReturn(List.of(res1));
        when(memberWorkoutLogRepository.findByReservationIdIn(List.of(201L)))
                .thenReturn(List.of(MemberWorkoutLog.builder().id(1L).reservationId(201L).suggestedPlanDayId(100L).build()));

        PendingWorkoutResponse response = workoutService.getPendingWorkout(MEMBER_ID);

        assertThat(response.status()).isEqualTo("NO_PENDING");
    }

    @Test
    void getPendingWorkout_conReservaSinLog_devuelveReadyConDiaUnoSugerido() {
        Reservation res1 = reservation(201L, LocalDate.of(2026, 10, 1));
        when(workoutPlanRepository.findByMemberIdAndActiveTrue(MEMBER_ID)).thenReturn(Optional.of(plan));
        when(planDayRepository.findByWorkoutPlanIdOrderByOrderIndexAsc(PLAN_ID)).thenReturn(List.of(day1, day2, day3));
        when(reservationRepository.findByMemberIdAndCheckedInAtIsNotNullOrderByClassDateDesc(MEMBER_ID))
                .thenReturn(List.of(res1));
        when(memberWorkoutLogRepository.findByReservationIdIn(List.of(201L))).thenReturn(List.of());
        when(memberWorkoutLogRepository.findBySuggestedPlanDayIdIn(List.of(100L, 101L, 102L))).thenReturn(List.of());

        PendingWorkoutResponse response = workoutService.getPendingWorkout(MEMBER_ID);

        assertThat(response.status()).isEqualTo("READY");
        assertThat(response.reservationId()).isEqualTo(201L);
        assertThat(response.suggestedPlanDayId()).isEqualTo(100L); // Día 1, sin logs previos
    }

    @Test
    void getPendingWorkout_reservaMasReciente_esLaElegidaIgnorandoUnaMasViejaSinLog() {
        // El diseño: "siempre la ÚLTIMA reserva con check-in que todavía no tiene log" — la más
        // vieja sin log simplemente queda sin anotar, nunca se vuelve atrás a buscarla.
        Reservation masNueva = reservation(202L, LocalDate.of(2026, 10, 5));
        Reservation masVieja = reservation(201L, LocalDate.of(2026, 10, 1));
        when(workoutPlanRepository.findByMemberIdAndActiveTrue(MEMBER_ID)).thenReturn(Optional.of(plan));
        when(planDayRepository.findByWorkoutPlanIdOrderByOrderIndexAsc(PLAN_ID)).thenReturn(List.of(day1, day2, day3));
        // El repo ya devuelve ordenado DESC por classDate, como hace la query real.
        when(reservationRepository.findByMemberIdAndCheckedInAtIsNotNullOrderByClassDateDesc(MEMBER_ID))
                .thenReturn(List.of(masNueva, masVieja));
        when(memberWorkoutLogRepository.findByReservationIdIn(List.of(202L, 201L))).thenReturn(List.of());
        when(memberWorkoutLogRepository.findBySuggestedPlanDayIdIn(anyList())).thenReturn(List.of());

        PendingWorkoutResponse response = workoutService.getPendingWorkout(MEMBER_ID);

        assertThat(response.reservationId()).isEqualTo(202L);
    }

    // ---- createLog: validaciones ----

    @Test
    void createLog_sinCheckIn_lanzaInvalidWorkoutLog() {
        Reservation sinCheckIn = Reservation.builder()
                .id(300L)
                .memberId(MEMBER_ID)
                .classDate(LocalDate.now())
                .status(ReservationStatus.BOOKED)
                .checkedInAt(null)
                .build();
        when(reservationRepository.findByIdAndMemberId(300L, MEMBER_ID)).thenReturn(Optional.of(sinCheckIn));

        assertThatThrownBy(() -> workoutService.createLog(MEMBER_ID, 300L, new WorkoutLogSaveRequest(100L, null, List.of())))
                .isInstanceOf(InvalidWorkoutLogException.class)
                .hasMessageContaining("asistencia");
    }

    @Test
    void createLog_reservaQueYaTieneLog_lanzaInvalidWorkoutLog() {
        Reservation yaLogueada = reservation(301L, LocalDate.now());
        when(reservationRepository.findByIdAndMemberId(301L, MEMBER_ID)).thenReturn(Optional.of(yaLogueada));
        when(memberWorkoutLogRepository.findByReservationId(301L))
                .thenReturn(Optional.of(MemberWorkoutLog.builder().id(1L).reservationId(301L).build()));

        assertThatThrownBy(() -> workoutService.createLog(MEMBER_ID, 301L, new WorkoutLogSaveRequest(100L, null, List.of())))
                .isInstanceOf(InvalidWorkoutLogException.class)
                .hasMessageContaining("ya tiene un registro");
    }

    @Test
    void createLog_sinPlanDayIdNiFreeText_lanzaInvalidWorkoutLog() {
        Reservation res = reservation(302L, LocalDate.now());
        when(reservationRepository.findByIdAndMemberId(302L, MEMBER_ID)).thenReturn(Optional.of(res));
        when(memberWorkoutLogRepository.findByReservationId(302L)).thenReturn(Optional.empty());
        when(workoutPlanRepository.findByMemberIdAndActiveTrue(MEMBER_ID)).thenReturn(Optional.of(plan));
        when(planDayRepository.findByWorkoutPlanIdOrderByOrderIndexAsc(PLAN_ID)).thenReturn(List.of(day1, day2, day3));
        when(memberWorkoutLogRepository.findBySuggestedPlanDayIdIn(anyList())).thenReturn(List.of());

        assertThatThrownBy(() -> workoutService.createLog(MEMBER_ID, 302L, new WorkoutLogSaveRequest(null, "  ", List.of())))
                .isInstanceOf(InvalidWorkoutLogException.class)
                .hasMessageContaining("elegir un día");
    }

    @Test
    void createLog_planDayIdDeOtroPlan_lanzaInvalidWorkoutLog() {
        Reservation res = reservation(303L, LocalDate.now());
        when(reservationRepository.findByIdAndMemberId(303L, MEMBER_ID)).thenReturn(Optional.of(res));
        when(memberWorkoutLogRepository.findByReservationId(303L)).thenReturn(Optional.empty());
        when(workoutPlanRepository.findByMemberIdAndActiveTrue(MEMBER_ID)).thenReturn(Optional.of(plan));
        when(planDayRepository.findByWorkoutPlanIdOrderByOrderIndexAsc(PLAN_ID)).thenReturn(List.of(day1, day2, day3));
        when(memberWorkoutLogRepository.findBySuggestedPlanDayIdIn(anyList())).thenReturn(List.of());

        assertThatThrownBy(() -> workoutService.createLog(MEMBER_ID, 303L, new WorkoutLogSaveRequest(999L, null, List.of())))
                .isInstanceOf(InvalidWorkoutLogException.class)
                .hasMessageContaining("no pertenece al plan");
    }

    // ---- getNextPlanDay (vía createLog encadenados): el refinamiento de ciclo cerrado en el diseño ----

    /** Simula el repo guardando en una lista mutable en memoria, para que cada createLog() vea
     *  los logs ya creados por la llamada anterior — mismo comportamiento que tendría Postgres. */
    private List<MemberWorkoutLog> wireStatefulLogRepository() {
        List<MemberWorkoutLog> saved = new ArrayList<>();
        doAnswer(inv -> {
                    MemberWorkoutLog log = inv.getArgument(0);
                    if (log.getId() == null) {
                        log.setId((long) (saved.size() + 1));
                    }
                    saved.add(log);
                    return log;
                })
                .when(memberWorkoutLogRepository)
                .save(any());
        lenient()
                .when(memberWorkoutLogRepository.findBySuggestedPlanDayIdIn(anyList()))
                .thenAnswer(inv -> new ArrayList<>(saved));
        return saved;
    }

    @Test
    void cicloCompletoDaVueltaAlDiaUnoTrasElUltimoDia() {
        wireStatefulLogRepository();
        when(workoutPlanRepository.findByMemberIdAndActiveTrue(MEMBER_ID)).thenReturn(Optional.of(plan));
        when(planDayRepository.findByWorkoutPlanIdOrderByOrderIndexAsc(PLAN_ID)).thenReturn(List.of(day1, day2, day3));
        when(memberWorkoutLogRepository.findByReservationId(any())).thenReturn(Optional.empty());

        Reservation res1 = reservation(401L, LocalDate.of(2026, 10, 1));
        Reservation res2 = reservation(402L, LocalDate.of(2026, 10, 3));
        Reservation res3 = reservation(403L, LocalDate.of(2026, 10, 5));
        Reservation res4 = reservation(404L, LocalDate.of(2026, 10, 7));
        when(reservationRepository.findByIdAndMemberId(401L, MEMBER_ID)).thenReturn(Optional.of(res1));
        when(reservationRepository.findByIdAndMemberId(402L, MEMBER_ID)).thenReturn(Optional.of(res2));
        when(reservationRepository.findByIdAndMemberId(403L, MEMBER_ID)).thenReturn(Optional.of(res3));
        when(reservationRepository.findByIdAndMemberId(404L, MEMBER_ID)).thenReturn(Optional.of(res4));
        List<Reservation> allReservations = List.of(res1, res2, res3, res4);
        lenient()
                .when(reservationRepository.findAllById(anyList()))
                .thenAnswer(inv -> {
                    List<Long> ids = inv.getArgument(0);
                    return allReservations.stream().filter(r -> ids.contains(r.getId())).toList();
                });

        MemberWorkoutLogResponse log1 =
                workoutService.createLog(MEMBER_ID, 401L, new WorkoutLogSaveRequest(day1.getId(), null, List.of()));
        assertThat(log1.suggestedPlanDayId()).isEqualTo(day1.getId());

        MemberWorkoutLogResponse log2 =
                workoutService.createLog(MEMBER_ID, 402L, new WorkoutLogSaveRequest(day2.getId(), null, List.of()));
        assertThat(log2.suggestedPlanDayId()).isEqualTo(day2.getId());

        MemberWorkoutLogResponse log3 =
                workoutService.createLog(MEMBER_ID, 403L, new WorkoutLogSaveRequest(day3.getId(), null, List.of()));
        assertThat(log3.suggestedPlanDayId()).isEqualTo(day3.getId());

        // Tras el día 3 (el último), la sugerencia da la vuelta al día 1 — no sigue a un día 4
        // inexistente.
        MemberWorkoutLogResponse log4 =
                workoutService.createLog(MEMBER_ID, 404L, new WorkoutLogSaveRequest(day1.getId(), null, List.of()));
        assertThat(log4.suggestedPlanDayId()).isEqualTo(day1.getId());
    }

    @Test
    void desvioConTextoLibreReiniciaElCicloADiaUno() {
        wireStatefulLogRepository();
        when(workoutPlanRepository.findByMemberIdAndActiveTrue(MEMBER_ID)).thenReturn(Optional.of(plan));
        when(planDayRepository.findByWorkoutPlanIdOrderByOrderIndexAsc(PLAN_ID)).thenReturn(List.of(day1, day2, day3));
        when(memberWorkoutLogRepository.findByReservationId(any())).thenReturn(Optional.empty());

        Reservation res1 = reservation(411L, LocalDate.of(2026, 10, 1));
        Reservation res2 = reservation(412L, LocalDate.of(2026, 10, 3));
        Reservation res3 = reservation(413L, LocalDate.of(2026, 10, 5));
        when(reservationRepository.findByIdAndMemberId(411L, MEMBER_ID)).thenReturn(Optional.of(res1));
        when(reservationRepository.findByIdAndMemberId(412L, MEMBER_ID)).thenReturn(Optional.of(res2));
        when(reservationRepository.findByIdAndMemberId(413L, MEMBER_ID)).thenReturn(Optional.of(res3));
        List<Reservation> allReservations = List.of(res1, res2, res3);
        lenient()
                .when(reservationRepository.findAllById(anyList()))
                .thenAnswer(inv -> {
                    List<Long> ids = inv.getArgument(0);
                    return allReservations.stream().filter(r -> ids.contains(r.getId())).toList();
                });

        // Día 1 aceptado normalmente.
        MemberWorkoutLogResponse log1 =
                workoutService.createLog(MEMBER_ID, 411L, new WorkoutLogSaveRequest(day1.getId(), null, List.of()));
        assertThat(log1.suggestedPlanDayId()).isEqualTo(day1.getId());

        // Se desvía con texto libre en vez del Día 2 sugerido.
        MemberWorkoutLogResponse log2 = workoutService.createLog(
                MEMBER_ID, 412L, new WorkoutLogSaveRequest(null, "Remo libre", List.of()));
        assertThat(log2.suggestedPlanDayId()).isEqualTo(day2.getId()); // lo que se LE sugirió
        assertThat(log2.planDayId()).isNull(); // pero no lo aceptó
        assertThat(log2.freeTextLabel()).isEqualTo("Remo libre");

        // El desvío reinicia el ciclo: la próxima sugerencia vuelve a Día 1, NO a Día 3.
        MemberWorkoutLogResponse log3 =
                workoutService.createLog(MEMBER_ID, 413L, new WorkoutLogSaveRequest(day1.getId(), null, List.of()));
        assertThat(log3.suggestedPlanDayId()).isEqualTo(day1.getId());
    }

    // ---- updateLog: corregible, valida contra el plan AL QUE PERTENECE el log ----

    @Test
    void updateLog_logInexistente_lanzaWorkoutLogNotFound() {
        when(memberWorkoutLogRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workoutService.updateLog(MEMBER_ID, 999L, new WorkoutLogSaveRequest(100L, null, List.of())))
                .isInstanceOf(WorkoutLogNotFoundException.class);
    }

    @Test
    void updateLog_corrigeDeTextoLibreAEligirUnDiaDelPlanOriginal() {
        MemberWorkoutLog logExistente = MemberWorkoutLog.builder()
                .id(5L)
                .reservationId(501L)
                .suggestedPlanDayId(day2.getId())
                .planDayId(null)
                .freeTextLabel("Remo")
                .exercisesLog(List.of())
                .createdAt(Instant.now())
                .build();
        Reservation res = reservation(501L, LocalDate.of(2026, 10, 3));

        when(memberWorkoutLogRepository.findById(5L)).thenReturn(Optional.of(logExistente));
        when(reservationRepository.findByIdAndMemberId(501L, MEMBER_ID)).thenReturn(Optional.of(res));
        // El plan de ESTE log se resuelve por su suggestedPlanDayId -> workoutPlanId, no por el
        // plan activo actual (que podría ya haber sido reemplazado).
        when(planDayRepository.findById(day2.getId())).thenReturn(Optional.of(day2));
        when(planDayRepository.findByWorkoutPlanIdOrderByOrderIndexAsc(PLAN_ID)).thenReturn(List.of(day1, day2, day3));
        when(memberWorkoutLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MemberWorkoutLogResponse updated =
                workoutService.updateLog(MEMBER_ID, 5L, new WorkoutLogSaveRequest(day2.getId(), null, List.of()));

        assertThat(updated.planDayId()).isEqualTo(day2.getId());
        assertThat(updated.freeTextLabel()).isNull();
        assertThat(updated.suggestedPlanDayId()).isEqualTo(day2.getId()); // no cambia, es histórico
    }
}
