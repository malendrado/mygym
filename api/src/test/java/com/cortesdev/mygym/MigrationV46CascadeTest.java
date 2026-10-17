package com.cortesdev.mygym;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.GymClosure;
import com.cortesdev.mygym.models.GymClosureBlock;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymClosureBlockRepository;
import com.cortesdev.mygym.repositories.GymClosureRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import jakarta.persistence.EntityManager;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * V46 (auditoría 2026-10-17): sin ON DELETE en las claves foráneas de los cierres, borrar un gimnasio que tuvo un
 * cierre, o un bloque que aparece en uno, fallaba. Corre contra la base real (necesita las variables de Supabase o
 * un Postgres local), igual que el resto de las pruebas que levantan Spring.
 */
@SpringBootTest
@Transactional
class MigrationV46CascadeTest {

    @Autowired private GymRepository gymRepository;
    @Autowired private GymBlockRepository gymBlockRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private GymClosureRepository closureRepository;
    @Autowired private GymClosureBlockRepository closureBlockRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private EntityManager entityManager;

    private Gym gym;
    private GymBlock block;
    private GymClosure closure;
    private GymClosureBlock link;
    private Reservation cancelledByClosure;

    @BeforeEach
    void aGymWithAPartialClosureAndAReservationItCancelled() {
        gym = gymRepository.save(Gym.builder()
                .name("Gym cierres").slug("gym-cierres-" + System.nanoTime()).maxUsers(10).active(true)
                .googleLoginEnabled(true).bookingWindowMinutes(30).cancellationWindowMinutes(30).build());
        block = gymBlockRepository.save(GymBlock.builder()
                .gymId(gym.getId()).label("Yoga").dayOfWeek(DayOfWeek.MONDAY)
                .startTime(LocalTime.of(19, 0)).endTime(LocalTime.of(20, 0)).capacity(5).active(true).build());
        AppUser member = appUserRepository.save(AppUser.builder()
                .email("socio-" + System.nanoTime() + "@test.com").name("Socio").role(Role.MEMBER)
                .gymId(gym.getId()).active(true).build());
        closure = closureRepository.save(GymClosure.builder()
                .gymId(gym.getId()).startDate(LocalDate.now().plusDays(1)).endDate(LocalDate.now().plusDays(1))
                .wholeDays(false).reason("Mantención").createdByEmail("dueno@test.com").createdByRole("GYM_ADMIN")
                .createdAt(Instant.now()).build());
        link = closureBlockRepository.save(GymClosureBlock.builder()
                .closureId(closure.getId()).gymBlockId(block.getId()).build());
        cancelledByClosure = reservationRepository.save(Reservation.builder()
                .gymBlockId(block.getId()).memberId(member.getId()).classDate(LocalDate.now().plusDays(1))
                .status(ReservationStatus.CANCELLED).cancelledByClosureId(closure.getId()).build());
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void deletingAGymThatHadAClosureNoLongerFailsAndTakesTheClosureWithIt() {
        assertThatCode(() -> {
                    gymRepository.deleteById(gym.getId());
                    gymRepository.flush();
                })
                .doesNotThrowAnyException();
        entityManager.clear();

        assertThat(closureRepository.findById(closure.getId())).isEmpty();
        assertThat(closureBlockRepository.findById(link.getId())).isEmpty();
        assertThat(gymBlockRepository.findById(block.getId())).isEmpty();
    }

    @Test
    void deletingABlockThatAppearsInAClosureNoLongerFailsAndOnlyDropsTheLink() {
        assertThatCode(() -> {
                    gymBlockRepository.deleteById(block.getId());
                    gymBlockRepository.flush();
                })
                .doesNotThrowAnyException();
        entityManager.clear();

        assertThat(closureBlockRepository.findById(link.getId())).isEmpty();
        assertThat(closureRepository.findById(closure.getId())).isPresent(); // el cierre en sí queda como historial
    }

    @Test
    void aReservationCancelledByAClosureKeepsItsHistoryWhenTheClosureIsDeleted() {
        closureRepository.deleteById(closure.getId());
        closureRepository.flush();
        entityManager.clear();

        Reservation reloaded = reservationRepository.findById(cancelledByClosure.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reloaded.getCancelledByClosureId()).isNull();
    }
}
