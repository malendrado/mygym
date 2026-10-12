package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.MemberFixedClass;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.FixedClassesSaveResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.MemberFixedClassRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.services.exception.GymBlockNotFoundException;
import com.cortesdev.mygym.services.exception.InvalidFixedClassException;
import com.cortesdev.mygym.services.exception.MemberNotFoundException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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

/** Unidades puras (Mockito, sin DB): reemplazo del set de clases fijas de un socio. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FixedClassReplaceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Santiago");
    private static final Long GYM_ID = 2L;
    private static final Long MEMBER_ID = 4L;

    @Mock private MemberFixedClassRepository fixedClassRepository;
    @Mock private GymBlockRepository gymBlockRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private GymRepository gymRepository;
    @Mock private ReservationService reservationService;
    @Mock private MemberService memberService;
    @Mock private GymClosureService gymClosureService;
    @Mock private WaitlistService waitlistService;
    @Mock private MemberLifecycleEmailService emailService;

    @InjectMocks private FixedClassService service;

    private final AppUser member = AppUser.builder().id(MEMBER_ID).gymId(GYM_ID).role(Role.MEMBER).name("Camila").build();

    private GymBlock block(long id, String label, DayOfWeek day, int hour) {
        return GymBlock.builder().id(id).gymId(GYM_ID).label(label).dayOfWeek(day)
                .startTime(LocalTime.of(hour, 0)).endTime(LocalTime.of(hour + 1, 0)).capacity(10).active(true).build();
    }

    @BeforeEach
    void setUp() {
        when(appUserRepository.findByIdAndGymId(MEMBER_ID, GYM_ID)).thenReturn(Optional.of(member));
    }

    @Test
    void twoClassesTheSameDayAreRejected() {
        when(gymBlockRepository.findByIdAndGymId(1L, GYM_ID)).thenReturn(Optional.of(block(1L, "GAP", DayOfWeek.MONDAY, 19)));
        when(gymBlockRepository.findByIdAndGymId(2L, GYM_ID)).thenReturn(Optional.of(block(2L, "CrossFit", DayOfWeek.MONDAY, 19)));

        assertThatThrownBy(() -> service.replaceForMember(GYM_ID, MEMBER_ID, List.of(1L, 2L), 9L))
                .isInstanceOf(InvalidFixedClassException.class)
                .hasMessageContaining("una clase fija por día");
        verify(fixedClassRepository, never()).save(any());
    }

    @Test
    void aBlockOfAnotherGymIsReportedAsNotFound() {
        when(gymBlockRepository.findByIdAndGymId(99L, GYM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.replaceForMember(GYM_ID, MEMBER_ID, List.of(99L), 9L))
                .isInstanceOf(GymBlockNotFoundException.class);
    }

    @Test
    void aMemberOfAnotherGymIsReportedAsNotFound() {
        when(appUserRepository.findByIdAndGymId(55L, GYM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.replaceForMember(GYM_ID, 55L, List.of(), 9L))
                .isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    void inactiveBlocksAreRejected() {
        GymBlock inactive = block(1L, "GAP", DayOfWeek.MONDAY, 19);
        inactive.setActive(false);
        when(gymBlockRepository.findByIdAndGymId(1L, GYM_ID)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.replaceForMember(GYM_ID, MEMBER_ID, List.of(1L), 9L))
                .isInstanceOf(InvalidFixedClassException.class)
                .hasMessageContaining("desactivada");
    }

    @Test
    void removingAClassCancelsItsFutureReservationsFreesTheSpotAndKeepsTheOtherClasses() {
        MemberFixedClass keep = MemberFixedClass.builder().id(10L).memberId(MEMBER_ID).gymBlockId(1L).build();
        MemberFixedClass drop = MemberFixedClass.builder().id(11L).memberId(MEMBER_ID).gymBlockId(2L).build();
        GymBlock monday = block(1L, "GAP", DayOfWeek.MONDAY, 19);
        GymBlock wednesday = block(2L, "GAP", DayOfWeek.WEDNESDAY, 19);
        when(gymBlockRepository.findByIdAndGymId(1L, GYM_ID)).thenReturn(Optional.of(monday));
        when(fixedClassRepository.findByMemberId(MEMBER_ID)).thenReturn(List.of(keep, drop));
        when(gymBlockRepository.findAllById(any())).thenReturn(List.of(wednesday));

        LocalDate nextWeek = LocalDate.now(ZONE).plusDays(7);
        Reservation fromDropped = Reservation.builder()
                .id(100L).gymBlockId(2L).memberId(MEMBER_ID).classDate(nextWeek)
                .status(ReservationStatus.BOOKED).fixedClassId(11L).build();
        Reservation fromKept = Reservation.builder()
                .id(101L).gymBlockId(1L).memberId(MEMBER_ID).classDate(nextWeek)
                .status(ReservationStatus.BOOKED).fixedClassId(10L).build();
        Reservation normal = Reservation.builder()
                .id(102L).gymBlockId(2L).memberId(MEMBER_ID).classDate(nextWeek)
                .status(ReservationStatus.BOOKED).build();
        when(reservationRepository.findByMemberIdAndStatusAndClassDateGreaterThanEqual(
                        MEMBER_ID, ReservationStatus.BOOKED, LocalDate.now(ZONE)))
                .thenReturn(List.of(fromDropped, fromKept, normal));
        when(memberService.hasActiveMembership(member)).thenReturn(false);

        FixedClassesSaveResponse result = service.replaceForMember(GYM_ID, MEMBER_ID, List.of(1L), 9L);

        assertThat(result.cancelled()).isEqualTo(1);
        assertThat(fromDropped.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(fromDropped.getFixedClassId()).isNull();
        assertThat(fromKept.getStatus()).isEqualTo(ReservationStatus.BOOKED);
        assertThat(normal.getStatus()).isEqualTo(ReservationStatus.BOOKED);
        verify(waitlistService).onSpotFreed(2L, nextWeek);
        verify(fixedClassRepository).deleteAll(List.of(drop));
        // La que ya tenía se conserva (mismo id): no se vuelve a guardar.
        verify(fixedClassRepository, never()).save(any());
        assertThat(result.noActivePlan()).isTrue();
    }

    @Test
    void addingAClassStoresItWithWhoCreatedIt() {
        GymBlock monday = block(1L, "GAP", DayOfWeek.MONDAY, 19);
        when(gymBlockRepository.findByIdAndGymId(1L, GYM_ID)).thenReturn(Optional.of(monday));
        when(fixedClassRepository.findByMemberId(MEMBER_ID)).thenReturn(List.of());

        service.replaceForMember(GYM_ID, MEMBER_ID, List.of(1L), 9L);

        verify(fixedClassRepository).save(org.mockito.ArgumentMatchers.argThat(
                f -> f.getGymBlockId().equals(1L) && f.getMemberId().equals(MEMBER_ID)
                        && f.getGymId().equals(GYM_ID) && f.getCreatedByUserId().equals(9L)));
    }
}
