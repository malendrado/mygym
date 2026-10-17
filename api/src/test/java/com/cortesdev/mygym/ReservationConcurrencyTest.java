package com.cortesdev.mygym;

import static org.assertj.core.api.Assertions.assertThat;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.GymPlan;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.ReservationCreateRequest;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymPlanRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.services.ReservationService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Reservas SIMULTÁNEAS contra la base real (auditoría 2026-10-17). Sin los bloqueos de fila de book(), N llamadas en
 * paralelo contaban todas "queda 1 cupo" (o "me quedan 2 clases") y pasaban el tope. Esta prueba NO es transaccional a
 * propósito: cada reserva confirma en su propia transacción, como en producción, y al final se borra el gimnasio
 * (las filas hijas caen en cascada).
 */
@SpringBootTest
class ReservationConcurrencyTest {

    private static final ZoneId ZONE = ZoneId.of("America/Santiago");

    @Autowired private ReservationService reservationService;
    @Autowired private GymRepository gymRepository;
    @Autowired private GymBlockRepository gymBlockRepository;
    @Autowired private GymPlanRepository gymPlanRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private ReservationRepository reservationRepository;

    private Long gymId;

    @AfterEach
    void cleanUp() {
        if (gymId != null) {
            gymRepository.deleteById(gymId);
        }
    }

    private Gym newGym() {
        Gym gym = gymRepository.save(Gym.builder()
                .name("Gym concurrencia").slug("gym-concurrencia-" + System.nanoTime()).maxUsers(100).active(true)
                .googleLoginEnabled(true).bookingWindowMinutes(0).cancellationWindowMinutes(0).build());
        gymId = gym.getId();
        return gym;
    }

    private GymBlock newBlock(LocalDate date, LocalTime start, int capacity) {
        return gymBlockRepository.save(GymBlock.builder()
                .gymId(gymId).label("Clase " + start).dayOfWeek(date.getDayOfWeek()).startTime(start)
                .endTime(start.plusHours(1)).capacity(capacity).active(true).build());
    }

    private AppUser newMember(Long planId) {
        return appUserRepository.save(AppUser.builder()
                .email("socio-" + System.nanoTime() + "@test.com").name("Socio").role(Role.MEMBER).gymId(gymId)
                .active(true).planId(planId).paidAt(Instant.now())
                .rut("12.345.678-5").address("Av. Siempre Viva 742, Ñuñoa").phone("+56912345678")
                .birthDate(LocalDate.of(1990, 5, 20)).emergencyContactName("Marta Pérez")
                .emergencyContactPhone("+56987654321").build());
    }

    /** Lanza todas las tareas a la vez y devuelve cuántas terminaron sin excepción. */
    private int runAtTheSameTime(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (Callable<Object> task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    task.call();
                    succeeded.incrementAndGet();
                } catch (Exception expectedRefusal) {
                    // CapacityExceeded, MonthlyQuotaExceeded...: es justo lo que se espera de los que sobran.
                }
                return null;
            }));
        }
        ready.await(10, TimeUnit.SECONDS);
        go.countDown();
        for (Future<?> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();
        return succeeded.get();
    }

    @Test
    void eightMembersRacingForTheLastSeatLeaveExactlyOneReservation() throws Exception {
        newGym();
        LocalDate date = LocalDate.now(ZONE).plusDays(2);
        GymBlock block = newBlock(date, LocalTime.of(12, 0), 1);
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            AppUser member = newMember(null);
            tasks.add(() -> reservationService.book(gymId, member.getId(), new ReservationCreateRequest(block.getId(), date)));
        }

        int succeeded = runAtTheSameTime(tasks);

        assertThat(succeeded).isEqualTo(1);
        assertThat(reservationRepository.countByGymBlockIdAndClassDateAndStatus(
                        block.getId(), date, ReservationStatus.BOOKED))
                .isEqualTo(1);
    }

    @Test
    void oneMemberBookingSixDifferentClassesAtOnceNeverPassesTheirTwoClassPlan() throws Exception {
        newGym();
        GymPlan plan = gymPlanRepository.save(GymPlan.builder()
                .gymId(gymId).name("Plan 2 clases").priceClp(20_000).monthlyClasses(2).active(true).build());
        AppUser member = newMember(plan.getId());
        LocalDate date = LocalDate.now(ZONE).plusDays(2);
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int hour = 8; hour < 14; hour++) {
            GymBlock block = newBlock(date, LocalTime.of(hour, 0), 10);
            tasks.add(() -> reservationService.book(gymId, member.getId(), new ReservationCreateRequest(block.getId(), date)));
        }

        int succeeded = runAtTheSameTime(tasks);

        assertThat(succeeded).isEqualTo(2);
        assertThat(reservationRepository
                        .findByMemberIdAndStatusAndClassDateGreaterThanEqualOrderByClassDateAsc(
                                member.getId(), ReservationStatus.BOOKED, LocalDate.now(ZONE))
                        .size())
                .isEqualTo(2);
    }
}
