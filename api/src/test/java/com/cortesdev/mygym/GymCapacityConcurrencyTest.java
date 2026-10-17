package com.cortesdev.mygym;

import static org.assertj.core.api.Assertions.assertThat;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.AdminCreateRequest;
import com.cortesdev.mygym.models.dto.MemberCreateRequest;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.services.GymService;
import com.cortesdev.mygym.services.MemberService;
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
 * Altas SIMULTÁNEAS contra la base real (auditoría 2026-10-17): los chequeos de cupo contaban y después insertaban sin
 * ningún bloqueo, así que N peticiones en paralelo pasaban todas el conteo y se pasaban del tope. No es transaccional a
 * propósito: cada alta confirma en su propia transacción, como en producción.
 */
@SpringBootTest
class GymCapacityConcurrencyTest {

    @Autowired private GymService gymService;
    @Autowired private MemberService memberService;
    @Autowired private GymRepository gymRepository;
    @Autowired private AppUserRepository appUserRepository;

    private Long gymId;

    @AfterEach
    void cleanUp() {
        if (gymId != null) {
            gymRepository.deleteById(gymId);
        }
    }

    private Gym newGym(int maxUsers) {
        Gym gym = gymRepository.save(Gym.builder()
                .name("Gym cupos").slug("gym-cupos-" + System.nanoTime()).maxUsers(maxUsers).active(true)
                .googleLoginEnabled(true).bookingWindowMinutes(0).cancellationWindowMinutes(0).build());
        gymId = gym.getId();
        return gym;
    }

    private void addExisting(Role role, int count) {
        for (int i = 0; i < count; i++) {
            appUserRepository.save(AppUser.builder()
                    .email(role.name().toLowerCase() + i + "-" + System.nanoTime() + "@test.com").name("Existente")
                    .role(role).gymId(gymId).active(true).build());
        }
    }

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
                    // GymLimitReachedException: es justo lo que se espera de los que sobran.
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
    void sixAdminsAddingTheLastTeacherAtOnceLeaveExactlyTen() throws Exception {
        newGym(100);
        addExisting(Role.PROFESOR, 9);
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String email = "nuevo-prof-" + i + "-" + System.nanoTime() + "@test.com";
            tasks.add(() -> gymService.addProfesor(gymId, new AdminCreateRequest("Profe", email)));
        }

        int succeeded = runAtTheSameTime(tasks);

        assertThat(succeeded).isEqualTo(1);
        assertThat(appUserRepository.countByGymIdAndRole(gymId, Role.PROFESOR)).isEqualTo(10);
    }

    @Test
    void sixMembersAddedAtOnceNeverPassTheMemberQuota() throws Exception {
        newGym(3);
        addExisting(Role.MEMBER, 2); // queda 1 cupo
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String email = "nuevo-socio-" + i + "-" + System.nanoTime() + "@test.com";
            tasks.add(() -> memberService.createMember(gymId, new MemberCreateRequest("Socio", email)));
        }

        int succeeded = runAtTheSameTime(tasks);

        assertThat(succeeded).isEqualTo(1);
        assertThat(appUserRepository.countByGymIdAndRole(gymId, Role.MEMBER)).isEqualTo(3);
    }
}
