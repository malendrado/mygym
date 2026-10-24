package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.GymPlan;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymPlanRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DemoGymSeederTest {

    private static final long GYM_ID = 179L;

    private final GymRepository gyms = mock(GymRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final GymBlockRepository blocks = mock(GymBlockRepository.class);
    private final GymPlanRepository plans = mock(GymPlanRepository.class);
    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final DemoGymSeeder seeder = new DemoGymSeeder(gyms, users, blocks, plans, reservations);

    private final List<AppUser> members = new ArrayList<>();
    private final List<AppUser> staff = new ArrayList<>();
    private final List<Reservation> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Gym gym = Gym.builder().id(GYM_ID).slug("demo").build();
        when(gyms.findBySlug("demo")).thenReturn(Optional.of(gym));

        for (long i = 1; i <= 12; i++) {
            members.add(AppUser.builder().id(i).name("Demo Activo " + i).email("m" + i + "@x.cl").role(Role.MEMBER)
                    .gymId(GYM_ID).active(true).planId(1L).build());
        }
        // Una socia con plan de cupo limitado: no debe recibir reservas (consumiría sus clases).
        members.add(AppUser.builder().id(13L).name("Camila Fuentes").email("c@x.cl").role(Role.MEMBER)
                .gymId(GYM_ID).active(true).planId(2L).build());
        when(users.findByGymIdAndRole(GYM_ID, Role.MEMBER)).thenReturn(members);
        when(users.findByEmail(any())).thenAnswer(inv -> staff.stream().filter(u -> u.getEmail().equals(inv.getArgument(0))).findFirst());
        when(users.save(any(AppUser.class))).thenAnswer(inv -> {
            AppUser u = inv.getArgument(0);
            staff.add(u);
            return u;
        });

        when(plans.findByGymId(GYM_ID)).thenReturn(List.of(
                GymPlan.builder().id(1L).gymId(GYM_ID).name("Libre").active(true).monthlyClasses(null).build(),
                GymPlan.builder().id(2L).gymId(GYM_ID).name("Estudiante").active(true).monthlyClasses(12).build()));

        List<GymBlock> all = new ArrayList<>();
        long id = 100;
        for (DayOfWeek day : DayOfWeek.values()) {
            all.add(GymBlock.builder().id(id++).gymId(GYM_ID).label("Funcional").dayOfWeek(day)
                    .startTime(LocalTime.of(7, 0)).endTime(LocalTime.of(8, 0)).capacity(10).active(true).build());
        }
        when(blocks.findByGymId(GYM_ID)).thenReturn(all);

        when(reservations.findByGymBlockIdAndClassDateAndStatus(anyLong(), any(LocalDate.class), eq(ReservationStatus.BOOKED)))
                .thenAnswer(inv -> stored.stream()
                        .filter(r -> r.getGymBlockId().equals(inv.getArgument(0)) && r.getClassDate().equals(inv.getArgument(1)))
                        .toList());
        when(reservations.saveAll(any())).thenAnswer(inv -> {
            Iterable<Reservation> rs = inv.getArgument(0);
            rs.forEach(stored::add);
            return rs;
        });
        when(reservations.save(any(Reservation.class))).thenAnswer(inv -> {
            stored.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
    }

    @Test
    void giveRealUniqueNamesToPlaceholderMembersOnly() {
        seeder.refresh();

        assertThat(members.stream().map(AppUser::getName)).noneMatch(n -> n.startsWith("Demo"));
        assertThat(members.stream().map(AppUser::getName).collect(Collectors.toSet())).hasSize(members.size());
        assertThat(members.get(12).getName()).isEqualTo("Camila Fuentes");
    }

    @Test
    void createsTwoTeachersAndAnAdminWithoutDuplicatingThem() {
        seeder.refresh();
        seeder.refresh();

        assertThat(staff).hasSize(3);
        assertThat(staff.stream().filter(u -> u.getRole() == Role.PROFESOR)).hasSize(2);
        assertThat(staff.stream().filter(u -> u.getRole() == Role.GYM_ADMIN)).hasSize(1);
        assertThat(staff).allMatch(u -> u.getGymId() == GYM_ID && u.isActive() && u.getEmail().endsWith("@example.com"));
    }

    @Test
    void bookedClassesRespectCapacityUniquenessAndPlanLimit() {
        seeder.refresh();

        assertThat(stored).isNotEmpty();
        Set<String> keys = new HashSet<>();
        for (Reservation r : stored) {
            assertThat(keys.add(r.getGymBlockId() + "-" + r.getMemberId() + "-" + r.getClassDate())).isTrue();
            assertThat(r.getMemberId()).isNotEqualTo(13L);
            assertThat(r.getStatus()).isEqualTo(ReservationStatus.BOOKED);
        }
        assertThat(stored.stream().collect(Collectors.groupingBy(r -> r.getGymBlockId() + "-" + r.getClassDate(), Collectors.counting()))
                        .values())
                .allMatch(count -> count <= 10);
    }

    @Test
    void finishedClassesHaveAttendanceAndFutureOnesDoNot() {
        seeder.refresh();

        LocalDate today = LocalDate.now(ZoneId.of("America/Santiago"));
        List<Reservation> past = stored.stream().filter(r -> r.getClassDate().isBefore(today)).toList();
        List<Reservation> future = stored.stream().filter(r -> r.getClassDate().isAfter(today)).toList();
        assertThat(past).isNotEmpty();
        assertThat(past.stream().filter(r -> r.getCheckedInAt() != null)).isNotEmpty();
        assertThat(past.stream().filter(r -> r.getCheckedInAt() == null)).isNotEmpty();
        assertThat(future).isNotEmpty().allMatch(r -> r.getCheckedInAt() == null);
    }

    @Test
    void sampleMemberGetsUpcomingBookings() {
        seeder.refresh();

        LocalDate today = LocalDate.now(ZoneId.of("America/Santiago"));
        assertThat(stored.stream().filter(r -> r.getMemberId().equals(1L) && !r.getClassDate().isBefore(today))).isNotEmpty();
    }

    @Test
    void leavesOneUpcomingClassFullWithoutTheSampleMember() {
        seeder.refresh();

        LocalDate today = LocalDate.now(ZoneId.of("America/Santiago"));
        var perClass = stored.stream()
                .filter(r -> !r.getClassDate().isBefore(today))
                .collect(Collectors.groupingBy(r -> r.getGymBlockId() + "|" + r.getClassDate()));
        var full = perClass.values().stream().filter(list -> list.size() == 10).toList();
        assertThat(full).isNotEmpty();
        assertThat(full).anyMatch(list -> list.stream().noneMatch(r -> r.getMemberId().equals(1L)));
    }

    @Test
    void secondRunChangesNothing() {
        seeder.refresh();
        int after = stored.size();
        List<String> names = members.stream().map(AppUser::getName).toList();

        seeder.refresh();

        assertThat(stored).hasSize(after);
        assertThat(members.stream().map(AppUser::getName).toList()).isEqualTo(names);
    }

    @Test
    void doesNothingWhenThereIsNoDemoGym() {
        when(gyms.findBySlug("demo")).thenReturn(Optional.empty());

        seeder.refresh();

        verify(reservations, never()).saveAll(any());
        verify(users, never()).save(any(AppUser.class));
        assertThat(stored).isEmpty();
    }
}
