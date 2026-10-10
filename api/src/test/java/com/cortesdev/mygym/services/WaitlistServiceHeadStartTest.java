package com.cortesdev.mygym.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.ClassWaitlistEntry;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.ClassWaitlistRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unidades puras (Mockito, sin DB): la ventaja del primero de la lista de espera es POR GYM
 *  (antes una constante global de 10 min, ver V36). */
@ExtendWith(MockitoExtension.class)
class WaitlistServiceHeadStartTest {

    private static final Long GYM_ID = 5L;
    private static final Long BLOCK_ID = 9L;
    private static final LocalDate DATE = LocalDate.now(ZoneId.of("America/Santiago")).plusDays(1);

    @Mock private ClassWaitlistRepository waitlistRepository;
    @Mock private GymBlockRepository gymBlockRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private GymRepository gymRepository;
    @Mock private MemberLifecycleEmailService emailService;
    @InjectMocks private WaitlistService waitlistService;

    private Gym gym(int headStartMinutes) {
        return Gym.builder().id(GYM_ID).waitlistHeadStartMinutes(headStartMinutes).build();
    }

    private GymBlock block() {
        return GymBlock.builder().id(BLOCK_ID).gymId(GYM_ID).label("Yoga").capacity(5).build();
    }

    private ClassWaitlistEntry entry(long memberId, Instant notifiedAt) {
        return ClassWaitlistEntry.builder()
                .gymBlockId(BLOCK_ID)
                .memberId(memberId)
                .classDate(DATE)
                .notifiedAt(notifiedAt)
                .build();
    }

    private void stubScheduler(Gym gym, List<ClassWaitlistEntry> entries) {
        when(waitlistRepository.findByClassDateGreaterThanEqual(any(LocalDate.class))).thenReturn(entries);
        when(gymBlockRepository.findAllById(any())).thenReturn(List.of(block()));
        when(gymRepository.findAllById(any())).thenReturn(List.of(gym));
    }

    @Test
    void headStartNotYetExpiredForThisGymDoesNotEscalate() {
        // el cabeza fue avisado hace 20 min; este gym da 30 min de ventaja → todavía no se escala
        Instant twentyMinAgo = Instant.now().minus(20, ChronoUnit.MINUTES);
        stubScheduler(gym(30), List.of(entry(1, twentyMinAgo), entry(2, null)));

        waitlistService.escalateExpiredHeadStarts();

        verify(emailService, never()).sendWaitlistSpotOpen(any(), any(), any(), any());
    }

    @Test
    void headStartExpiredForThisGymEscalatesToTheRest() {
        // mismo caso (20 min), pero este gym da solo 10 de ventaja → ya venció y se avisa al resto
        Instant twentyMinAgo = Instant.now().minus(20, ChronoUnit.MINUTES);
        stubScheduler(gym(10), List.of(entry(1, twentyMinAgo), entry(2, null)));
        when(reservationRepository.countByGymBlockIdAndClassDateAndStatus(
                        BLOCK_ID, DATE, ReservationStatus.BOOKED))
                .thenReturn(3);
        when(appUserRepository.findById(2L)).thenReturn(Optional.of(AppUser.builder().id(2L).name("Ana").build()));

        waitlistService.escalateExpiredHeadStarts();

        verify(emailService).sendWaitlistSpotOpen(any(), any(), any(), eq(DATE));
    }

    @Test
    void zeroHeadStartNotifiesTheWholeListAtOnceWhenASpotFrees() {
        Gym g = gym(0);
        when(gymBlockRepository.findById(BLOCK_ID)).thenReturn(Optional.of(block()));
        when(reservationRepository.countByGymBlockIdAndClassDateAndStatus(
                        BLOCK_ID, DATE, ReservationStatus.BOOKED))
                .thenReturn(4); // capacidad 5 → 1 cupo libre
        when(waitlistRepository.findByGymBlockIdAndClassDateOrderByCreatedAtAsc(BLOCK_ID, DATE))
                .thenReturn(List.of(entry(1, null), entry(2, null), entry(3, null)));
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(g));
        when(appUserRepository.findById(anyLong()))
                .thenAnswer(inv -> Optional.of(AppUser.builder().id(inv.getArgument(0)).name("X").build()));

        waitlistService.onSpotFreed(BLOCK_ID, DATE);

        // sin cabeza de lista: los 3 reciben el aviso "cupo abierto", ninguno el de ventaja
        verify(emailService, org.mockito.Mockito.times(3)).sendWaitlistSpotOpen(any(), any(), any(), eq(DATE));
        verify(emailService, never()).sendWaitlistHeadStart(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void headStartEmailCarriesTheMinutesOfThatGym() {
        Gym g = gym(45);
        when(gymBlockRepository.findById(BLOCK_ID)).thenReturn(Optional.of(block()));
        when(reservationRepository.countByGymBlockIdAndClassDateAndStatus(
                        BLOCK_ID, DATE, ReservationStatus.BOOKED))
                .thenReturn(4);
        when(waitlistRepository.findByGymBlockIdAndClassDateOrderByCreatedAtAsc(BLOCK_ID, DATE))
                .thenReturn(List.of(entry(1, null), entry(2, null)));
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(g));
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(AppUser.builder().id(1L).name("Ana").build()));

        waitlistService.onSpotFreed(BLOCK_ID, DATE);

        verify(emailService).sendWaitlistHeadStart(any(), any(), any(), eq(DATE), eq(45L));
    }
}
