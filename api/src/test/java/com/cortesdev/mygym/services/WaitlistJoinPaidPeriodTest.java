package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.ClassWaitlistEntry;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.ClassWaitlistRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.services.exception.BookingWindowClosedException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Anotarse a la espera de una clase posterior al vencimiento del plan tiene la misma regla que reservarla. */
@ExtendWith(MockitoExtension.class)
class WaitlistJoinPaidPeriodTest {

    private static final Long GYM_ID = 5L;
    private static final Long BLOCK_ID = 9L;
    private static final Long MEMBER_ID = 3L;

    @Mock private ClassWaitlistRepository waitlistRepository;
    @Mock private GymBlockRepository gymBlockRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private GymRepository gymRepository;
    @Mock private MemberLifecycleEmailService emailService;
    @Mock private FixedWeekRule fixedWeekRule;
    @Mock private PushNotificationService pushService;
    @InjectMocks private WaitlistService waitlistService;

    private GymBlock block() {
        return GymBlock.builder().id(BLOCK_ID).gymId(GYM_ID).label("Yoga").capacity(5)
                .active(true).startTime(LocalTime.of(19, 0)).build();
    }

    @Test
    void joiningTheWaitlistForADatePastThePaidPeriodIsRefused() {
        LocalDate farAway = LocalDate.now(ZoneId.of("America/Santiago")).plusDays(40);
        when(gymBlockRepository.findByIdAndGymId(BLOCK_ID, GYM_ID)).thenReturn(Optional.of(block()));
        when(appUserRepository.findByIdAndGymId(MEMBER_ID, GYM_ID))
                .thenReturn(Optional.of(AppUser.builder().id(MEMBER_ID).gymId(GYM_ID)
                        .paidAt(Instant.now().minus(10, ChronoUnit.DAYS)).build()));

        assertThatThrownBy(() -> waitlistService.join(GYM_ID, MEMBER_ID, BLOCK_ID, farAway))
                .isInstanceOf(BookingWindowClosedException.class)
                .hasMessageStartingWith("Tu plan cubre hasta el ");
        verify(waitlistRepository, never()).save(any(ClassWaitlistEntry.class));
    }

    @Test
    void joiningForADateInsideThePaidPeriodStillWorks() {
        LocalDate tomorrow = LocalDate.now(ZoneId.of("America/Santiago")).plusDays(1);
        when(gymBlockRepository.findByIdAndGymId(BLOCK_ID, GYM_ID)).thenReturn(Optional.of(block()));
        when(appUserRepository.findByIdAndGymId(MEMBER_ID, GYM_ID))
                .thenReturn(Optional.of(AppUser.builder().id(MEMBER_ID).gymId(GYM_ID).paidAt(Instant.now()).build()));
        when(reservationRepository.countByGymBlockIdAndClassDateAndStatus(any(), any(), any())).thenReturn(5);

        waitlistService.join(GYM_ID, MEMBER_ID, BLOCK_ID, tomorrow);

        verify(waitlistRepository).save(any(ClassWaitlistEntry.class));
    }
}
