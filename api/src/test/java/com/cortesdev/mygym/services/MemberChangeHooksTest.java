package com.cortesdev.mygym.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.GymClosure;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.BlockUpdateRequest;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymClosureRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Que cada cambio del equipo (cerrar/abrir, cancelar, mover, quitar plan) llame de verdad al aviso al socio. */
@ExtendWith(MockitoExtension.class)
class MemberChangeHooksTest {

    private static final Long GYM_ID = 4L;
    private static final Long MEMBER_ID = 20L;
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Santiago"));

    @Mock private MemberChangeNotifier notifier;
    @Mock private GymRepository gymRepository;
    @Mock private GymBlockRepository gymBlockRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private GymClosureRepository gymClosureRepository;
    @Mock private WaitlistService waitlistService;
    @InjectMocks private GymService gymService;
    @InjectMocks private ReservationService reservationService;
    @InjectMocks private GymClosureService gymClosureService;

    private final Gym gym = Gym.builder().id(GYM_ID).name("XaloGym").active(true).build();

    private GymBlock block(boolean active) {
        return GymBlock.builder()
                .id(7L).gymId(GYM_ID).label("Funcional").dayOfWeek(DayOfWeek.THURSDAY)
                .startTime(LocalTime.of(19, 0)).endTime(LocalTime.of(20, 0)).capacity(10).active(active).build();
    }

    private BlockUpdateRequest update(DayOfWeek day, LocalTime start, boolean active) {
        return new BlockUpdateRequest("Funcional", day, start, start.plusHours(1), 10, null, null, null, active);
    }

    // ---- A) levantar un cierre ----

    @Test
    void liftingAClosureAnnouncesIt() {
        GymClosure closure = GymClosure.builder().id(3L).gymId(GYM_ID).endDate(TODAY.plusDays(2)).build();
        when(gymClosureRepository.findByIdAndGymId(3L, GYM_ID)).thenReturn(Optional.of(closure));
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(gym));

        gymClosureService.lift(GYM_ID, 3L);

        verify(notifier).closureLifted(gym, closure);
    }

    @Test
    void liftingAnAlreadyLiftedClosureDoesNotAnnounceItTwice() {
        GymClosure closure = GymClosure.builder().id(3L).gymId(GYM_ID).endDate(TODAY.plusDays(2)).liftedAt(java.time.Instant.now()).build();
        when(gymClosureRepository.findByIdAndGymId(3L, GYM_ID)).thenReturn(Optional.of(closure));

        gymClosureService.lift(GYM_ID, 3L);

        verify(notifier, never()).closureLifted(any(), any());
    }

    // ---- B) el equipo cancela una reserva ----

    @Test
    void cancellingAReservationAsStaffTellsTheMember() {
        GymBlock block = block(true);
        Reservation reservation = Reservation.builder()
                .id(9L).gymBlockId(7L).memberId(MEMBER_ID).classDate(TODAY.plusDays(1)).status(ReservationStatus.BOOKED).build();
        when(reservationRepository.findById(9L)).thenReturn(Optional.of(reservation));
        when(gymBlockRepository.findById(7L)).thenReturn(Optional.of(block));
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(gym));

        reservationService.cancelAsAdmin(GYM_ID, 9L);

        verify(notifier).reservationCancelledByStaff(gym, block, reservation);
    }

    @Test
    void cancellingAnAlreadyCancelledReservationDoesNotTellTheMemberAgain() {
        GymBlock block = block(true);
        Reservation reservation = Reservation.builder()
                .id(9L).gymBlockId(7L).memberId(MEMBER_ID).classDate(TODAY.plusDays(1)).status(ReservationStatus.CANCELLED).build();
        when(reservationRepository.findById(9L)).thenReturn(Optional.of(reservation));
        when(gymBlockRepository.findById(7L)).thenReturn(Optional.of(block));

        reservationService.cancelAsAdmin(GYM_ID, 9L);

        verify(notifier, never()).reservationCancelledByStaff(any(), any(), any());
    }

    // ---- C) cambio de horario ----

    @Test
    void changingTheTimeOfAClassTellsWhoHasABooking() {
        GymBlock block = block(true);
        when(gymRepository.existsById(GYM_ID)).thenReturn(true);
        when(gymBlockRepository.findByIdAndGymId(7L, GYM_ID)).thenReturn(Optional.of(block));
        when(gymBlockRepository.save(block)).thenReturn(block);
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(gym));

        gymService.updateBlock(GYM_ID, 7L, update(DayOfWeek.THURSDAY, LocalTime.of(19, 30), true));

        verify(notifier).blockRescheduled(gym, block, DayOfWeek.THURSDAY, LocalTime.of(19, 0));
    }

    @Test
    void editingOnlyTheCapacityOrNameTellsNobody() {
        GymBlock block = block(true);
        when(gymRepository.existsById(GYM_ID)).thenReturn(true);
        when(gymBlockRepository.findByIdAndGymId(7L, GYM_ID)).thenReturn(Optional.of(block));
        when(gymBlockRepository.save(block)).thenReturn(block);

        gymService.updateBlock(GYM_ID, 7L, update(DayOfWeek.THURSDAY, LocalTime.of(19, 0), true));

        verify(notifier, never()).blockRescheduled(any(), any(), any(), any());
        verify(notifier, never()).blockCancelled(any(), any());
    }

    // ---- D) clase desactivada o eliminada ----

    @Test
    void deactivatingAClassTellsWhoHasABooking() {
        GymBlock block = block(true);
        when(gymRepository.existsById(GYM_ID)).thenReturn(true);
        when(gymBlockRepository.findByIdAndGymId(7L, GYM_ID)).thenReturn(Optional.of(block));
        when(gymBlockRepository.save(block)).thenReturn(block);
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(gym));

        gymService.updateBlock(GYM_ID, 7L, update(DayOfWeek.THURSDAY, LocalTime.of(19, 0), false));

        verify(notifier).blockCancelled(gym, block);
        verify(notifier, never()).blockRescheduled(any(), any(), any(), any());
    }

    @Test
    void deletingAnActiveClassTellsBeforeTheBookingsDisappear() {
        GymBlock block = block(true);
        when(gymRepository.existsById(GYM_ID)).thenReturn(true);
        when(gymBlockRepository.findByIdAndGymId(7L, GYM_ID)).thenReturn(Optional.of(block));
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(gym));

        gymService.removeBlock(GYM_ID, 7L);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(notifier, gymBlockRepository);
        order.verify(notifier).blockCancelled(gym, block);
        order.verify(gymBlockRepository).delete(block);
    }

    @Test
    void deletingAnAlreadyInactiveClassDoesNotTellAgain() {
        GymBlock block = block(false);
        when(gymRepository.existsById(GYM_ID)).thenReturn(true);
        when(gymBlockRepository.findByIdAndGymId(7L, GYM_ID)).thenReturn(Optional.of(block));

        gymService.removeBlock(GYM_ID, 7L);

        verify(notifier, never()).blockCancelled(any(), any());
        verify(gymBlockRepository).delete(block);
    }

    // ---- E) quitar el plan ----

    @Test
    void removingAPlanTellsTheMemberAndCountsTheCancelledReservations() {
        AppUser member = AppUser.builder().id(MEMBER_ID).gymId(GYM_ID).role(Role.MEMBER).planId(5L).build();
        Reservation r1 = Reservation.builder().id(1L).memberId(MEMBER_ID).classDate(TODAY.plusDays(1)).status(ReservationStatus.BOOKED).build();
        Reservation r2 = Reservation.builder().id(2L).memberId(MEMBER_ID).classDate(TODAY.plusDays(2)).status(ReservationStatus.BOOKED).build();
        when(gymRepository.existsById(GYM_ID)).thenReturn(true);
        when(appUserRepository.findByIdAndGymId(MEMBER_ID, GYM_ID)).thenReturn(Optional.of(member));
        when(reservationRepository.findByMemberIdAndStatusAndClassDateGreaterThanEqual(eq(MEMBER_ID), eq(ReservationStatus.BOOKED), any()))
                .thenReturn(List.of(r1, r2));
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(gym));

        gymService.revokePlan(GYM_ID, MEMBER_ID);

        verify(notifier).planRemoved(gym, MEMBER_ID, 2);
    }

    @Test
    void removingThePlanOfAMemberWithoutOneTellsNobody() {
        AppUser member = AppUser.builder().id(MEMBER_ID).gymId(GYM_ID).role(Role.MEMBER).planId(null).build();
        when(gymRepository.existsById(GYM_ID)).thenReturn(true);
        when(appUserRepository.findByIdAndGymId(MEMBER_ID, GYM_ID)).thenReturn(Optional.of(member));
        when(reservationRepository.findByMemberIdAndStatusAndClassDateGreaterThanEqual(eq(MEMBER_ID), eq(ReservationStatus.BOOKED), any()))
                .thenReturn(List.of());

        gymService.revokePlan(GYM_ID, MEMBER_ID);

        verify(notifier, never()).planRemoved(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }
}
