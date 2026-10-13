package com.cortesdev.mygym.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.GymClosure;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymClosureRepository;
import com.cortesdev.mygym.repositories.GymPlanRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Cierre de emergencia: cada socio con clases canceladas recibe el correo Y el push (un solo aviso por socio). */
@ExtendWith(MockitoExtension.class)
class ClosureNotificationPushTest {

    private static final Long GYM_ID = 5L;
    private static final Long CLOSURE_ID = 11L;

    @Mock private GymClosureRepository gymClosureRepository;
    @Mock private GymRepository gymRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private GymBlockRepository gymBlockRepository;
    @Mock private GymPlanRepository gymPlanRepository;
    @Mock private MemberLifecycleEmailService emailService;
    @Mock private PushNotificationService pushService;
    @InjectMocks private ClosureNotificationService service;

    @Test
    void eachAffectedMemberGetsOnePushWithAllHisCancelledClasses() {
        GymClosure closure = GymClosure.builder().id(CLOSURE_ID).gymId(GYM_ID).reason("corte de luz").build();
        Gym gym = Gym.builder().id(GYM_ID).name("XaloGym").build();
        GymBlock block = GymBlock.builder().id(9L).gymId(GYM_ID).label("Funcional").startTime(LocalTime.of(19, 0)).build();
        when(gymClosureRepository.findById(CLOSURE_ID)).thenReturn(Optional.of(closure));
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(gym));
        when(reservationRepository.findByCancelledByClosureId(CLOSURE_ID))
                .thenReturn(List.of(
                        Reservation.builder().gymBlockId(9L).memberId(30L).classDate(LocalDate.of(2026, 10, 14)).build(),
                        Reservation.builder().gymBlockId(9L).memberId(30L).classDate(LocalDate.of(2026, 10, 15)).build()));
        when(gymBlockRepository.findAllById(any())).thenReturn(List.of(block));
        when(appUserRepository.findAllById(any()))
                .thenReturn(List.of(AppUser.builder().id(30L).name("Ana").role(Role.MEMBER).gymId(GYM_ID).email("a@x.cl").build()));

        service.notify(CLOSURE_ID, Role.GYM_ADMIN);

        verify(emailService).sendClosureCancellationMember(eq(gym), any(AppUser.class), eq("corte de luz"), any(), eq(false));
        ArgumentCaptor<PushMessage> pushed = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushService).notifyUsers(eq(List.of(30L)), pushed.capture());
        assertEquals("XaloGym canceló tus clases", pushed.getValue().title());
        assertEquals("closure-11", pushed.getValue().tag());
    }
}
