package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.MemberFixedClass;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.FixedClassGenerationResult;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.MemberFixedClassRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import java.time.Instant;
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

/** Unidades puras (Mockito, sin DB): generación idempotente de reservas desde las clases fijas. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FixedClassGenerationTest {

    private static final ZoneId ZONE = ZoneId.of("America/Santiago");
    private static final Long GYM_ID = 2L;
    private static final Long MEMBER_ID = 4L;
    private static final Long BLOCK_ID = 10L;
    private static final Long FIXED_ID = 77L;

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

    private final LocalDate today = LocalDate.now(ZONE);
    private final AppUser member = AppUser.builder()
            .id(MEMBER_ID).gymId(GYM_ID).role(Role.MEMBER).planId(5L).paidAt(Instant.now()).name("Camila").build();
    // Clase de mañana a las 19:00: siempre futura, y su día de la semana se repite ~4-5 veces en el período.
    private final GymBlock block = GymBlock.builder()
            .id(BLOCK_ID).gymId(GYM_ID).label("GAP").dayOfWeek(today.plusDays(1).getDayOfWeek())
            .startTime(LocalTime.of(19, 0)).endTime(LocalTime.of(20, 0)).capacity(10).active(true).build();
    private final MemberFixedClass fixed =
            MemberFixedClass.builder().id(FIXED_ID).gymId(GYM_ID).memberId(MEMBER_ID).gymBlockId(BLOCK_ID).build();

    @BeforeEach
    void setUp() {
        when(fixedClassRepository.findByMemberId(MEMBER_ID)).thenReturn(List.of(fixed));
        when(memberService.hasActiveMembership(member)).thenReturn(true);
        when(memberService.currentPeriod(member))
                .thenReturn(new MemberService.PeriodRange(today, today.plusMonths(1).minusDays(1)));
        when(gymBlockRepository.findAllById(any())).thenReturn(List.of(block));
        when(reservationRepository.findByMemberIdAndClassDateBetween(eq(MEMBER_ID), any(), any())).thenReturn(List.of());
        when(gymClosureService.scheduleFor(eq(GYM_ID), any(), any()))
                .thenReturn(new GymClosureService.ClosureSchedule(java.util.Set.of(), java.util.Set.of()));
    }

    private List<LocalDate> occurrenceDates() {
        return today.datesUntil(today.plusMonths(1)).filter(d -> d.getDayOfWeek() == block.getDayOfWeek()).toList();
    }

    @Test
    void stopsWhenThePlanQuotaRunsOutAndKeepsChronologicalOrder() {
        when(memberService.remainingClasses(member)).thenReturn(2);

        FixedClassGenerationResult result = service.generateForCurrentPeriod(member);

        assertThat(result.created()).isEqualTo(2);
        assertThat(result.stoppedByQuota()).isTrue();
        List<LocalDate> dates = occurrenceDates();
        verify(reservationService).createBooked(BLOCK_ID, MEMBER_ID, dates.get(0), FIXED_ID);
        verify(reservationService).createBooked(BLOCK_ID, MEMBER_ID, dates.get(1), FIXED_ID);
        verify(reservationService, times(2)).createBooked(anyLong(), anyLong(), any(), anyLong());
        verify(waitlistService, times(2)).clearOnBooked(eq(BLOCK_ID), any(), eq(MEMBER_ID));
    }

    @Test
    void unlimitedPlanReservesEveryDateOfThePeriod() {
        when(memberService.remainingClasses(member)).thenReturn(null);

        FixedClassGenerationResult result = service.generateForCurrentPeriod(member);

        assertThat(result.created()).isEqualTo(occurrenceDates().size());
        assertThat(result.stoppedByQuota()).isFalse();
    }

    @Test
    void doesNotDuplicateBookedDatesNorReimposeOnesTheMemberReleased() {
        when(memberService.remainingClasses(member)).thenReturn(null);
        List<LocalDate> dates = occurrenceDates();
        Reservation booked = Reservation.builder()
                .gymBlockId(BLOCK_ID).memberId(MEMBER_ID).classDate(dates.get(0)).status(ReservationStatus.BOOKED).build();
        // Cancelada por el socio (sin cierre): una fecha que soltó a propósito.
        Reservation released = Reservation.builder()
                .gymBlockId(BLOCK_ID).memberId(MEMBER_ID).classDate(dates.get(1))
                .status(ReservationStatus.CANCELLED).fixedClassId(FIXED_ID).build();
        when(reservationRepository.findByMemberIdAndClassDateBetween(eq(MEMBER_ID), any(), any()))
                .thenReturn(List.of(booked, released));

        FixedClassGenerationResult result = service.generateForCurrentPeriod(member);

        assertThat(result.created()).isEqualTo(dates.size() - 2);
        verify(reservationService, never()).createBooked(anyLong(), anyLong(), eq(dates.get(0)), any());
        verify(reservationService, never()).createBooked(anyLong(), anyLong(), eq(dates.get(1)), any());
    }

    @Test
    void aDateCancelledByAClosureIsNotConsideredReleased() {
        when(memberService.remainingClasses(member)).thenReturn(null);
        List<LocalDate> dates = occurrenceDates();
        Reservation byClosure = Reservation.builder()
                .gymBlockId(BLOCK_ID).memberId(MEMBER_ID).classDate(dates.get(0))
                .status(ReservationStatus.CANCELLED).fixedClassId(FIXED_ID).cancelledByClosureId(9L).build();
        when(reservationRepository.findByMemberIdAndClassDateBetween(eq(MEMBER_ID), any(), any()))
                .thenReturn(List.of(byClosure));

        FixedClassGenerationResult result = service.generateForCurrentPeriod(member);

        assertThat(result.created()).isEqualTo(dates.size());
    }

    @Test
    void closedDatesAreSkippedWithTheReasonAndDoNotConsumeQuota() {
        when(memberService.remainingClasses(member)).thenReturn(1);
        List<LocalDate> dates = occurrenceDates();
        when(gymClosureService.scheduleFor(eq(GYM_ID), any(), any())).thenReturn(new GymClosureService.ClosureSchedule(
                java.util.Set.of(), java.util.Set.of(BLOCK_ID + "|" + dates.get(0))));
        when(gymClosureService.closedReasonFor(GYM_ID, BLOCK_ID, dates.get(0))).thenReturn("Corte de luz");

        FixedClassGenerationResult result = service.generateForCurrentPeriod(member);

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.skipped()).hasSize(1);
        assertThat(result.skipped().get(0).reason()).isEqualTo("Cierre: Corte de luz");
        verify(reservationService).createBooked(BLOCK_ID, MEMBER_ID, dates.get(1), FIXED_ID);
    }

    @Test
    void memberWithoutActivePlanKeepsTheClassesButGetsNoReservations() {
        when(memberService.hasActiveMembership(member)).thenReturn(false);

        FixedClassGenerationResult result = service.generateForCurrentPeriod(member);

        assertThat(result.created()).isZero();
        assertThat(result.noActivePlan()).isTrue();
        verify(reservationService, never()).createBooked(anyLong(), anyLong(), any(), any());
        verify(emailService, never()).sendFixedClassesScheduled(any(), any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void deactivatedBlockGeneratesNothing() {
        when(memberService.remainingClasses(member)).thenReturn(null);
        block.setActive(false);

        FixedClassGenerationResult result = service.generateForCurrentPeriod(member);

        assertThat(result.created()).isZero();
        verify(reservationService, never()).createBooked(anyLong(), anyLong(), any(), any());
    }

    @Test
    void oneSummaryEmailIsSentOnlyWhenSomethingWasCreated() {
        when(memberService.remainingClasses(member)).thenReturn(null);
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(Gym.builder().id(GYM_ID).name("XaloGym").build()));

        service.generateForCurrentPeriod(member);

        verify(emailService).sendFixedClassesScheduled(
                any(Gym.class), eq(member), anyString(), eq(occurrenceDates().size()), anyString(), anyString(), any());
    }

    @Test
    void aFailureInTheRenewalHookNeverPropagatesToThePayment() {
        when(appUserRepository.findById(MEMBER_ID)).thenThrow(new IllegalStateException("db caída"));

        service.onMembershipPaid(new MembershipPaidEvent(MEMBER_ID)); // no debe lanzar

        verify(reservationService, never()).createBooked(anyLong(), anyLong(), any(), any());
    }

    @Test
    void renewalHookReservesTheClassesOfTheNewPeriod() {
        when(memberService.remainingClasses(member)).thenReturn(null);
        when(appUserRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        service.onMembershipPaid(new MembershipPaidEvent(MEMBER_ID));

        verify(reservationService, times(occurrenceDates().size())).createBooked(anyLong(), anyLong(), any(), anyLong());
    }
}
