package com.cortesdev.mygym.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymPlan;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.repositories.GymPlanRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unidades puras (Mockito, sin DB): aviso por email cuando la reserva usa la ÚLTIMA clase del período. */
@ExtendWith(MockitoExtension.class)
class ClassesExhaustedNotificationTest {

    private static final ZoneId ZONE = ZoneId.of("America/Santiago");
    private static final Long GYM_ID = 2L;

    @Mock private MemberService memberService;
    @Mock private GymRepository gymRepository;
    @Mock private GymPlanRepository gymPlanRepository;
    @Mock private MemberLifecycleEmailService emailService;

    private final Gym gym = Gym.builder().id(GYM_ID).name("XaloGym").build();
    private final GymPlan plan = GymPlan.builder().id(5L).gymId(GYM_ID).name("Plan Básico").monthlyClasses(8).build();

    private AppUser member() {
        return AppUser.builder().id(4L).gymId(GYM_ID).role(Role.MEMBER).planId(5L).paidAt(Instant.now()).build();
    }

    private ReservationService reservationService() {
        return new ReservationService(
                null, null, gymRepository, null, memberService, null, null, null, gymPlanRepository, emailService);
    }

    @Test
    void usingTheLastClassSendsTheExhaustedEmail() {
        AppUser m = member();
        when(memberService.remainingClasses(m)).thenReturn(0);
        when(gymPlanRepository.findById(5L)).thenReturn(Optional.of(plan));
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(gym));
        when(memberService.periodEnd(m)).thenReturn(ZonedDateTime.now(ZONE).plusDays(10));

        reservationService().notifyIfClassesExhausted(GYM_ID, m);

        verify(emailService).sendClassesExhaustedMember(eq(gym), eq(m), eq(plan), any(String.class));
    }

    @Test
    void classesLeftOrUnlimitedPlanSendsNothing() {
        AppUser m = member();
        for (Integer remaining : new Integer[] {3, null}) {
            when(memberService.remainingClasses(m)).thenReturn(remaining);

            reservationService().notifyIfClassesExhausted(GYM_ID, m);
        }

        verify(emailService, never()).sendClassesExhaustedMember(any(), any(), any(), any());
    }
}
