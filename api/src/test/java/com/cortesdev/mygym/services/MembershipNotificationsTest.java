package com.cortesdev.mygym.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymPlan;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymPlanRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unidades puras (Mockito, sin DB): avisos de vencimiento a 3 días y aviso de clases agotadas. */
@ExtendWith(MockitoExtension.class)
class MembershipNotificationsTest {

    private static final ZoneId ZONE = ZoneId.of("America/Santiago");
    private static final Long GYM_ID = 2L;

    @Mock private AppUserRepository appUserRepository;
    @Mock private MemberService memberService;
    @Mock private GymRepository gymRepository;
    @Mock private GymPlanRepository gymPlanRepository;
    @Mock private MemberLifecycleEmailService emailService;
    @InjectMocks private MembershipReminderJob reminderJob;

    private final Gym gym = Gym.builder().id(GYM_ID).name("XaloGym").build();
    private final GymPlan plan = GymPlan.builder().id(5L).gymId(GYM_ID).name("Plan Básico").monthlyClasses(8).build();

    private AppUser member(long id) {
        return AppUser.builder()
                .id(id)
                .gymId(GYM_ID)
                .role(Role.MEMBER)
                .planId(5L)
                .paidAt(Instant.now())
                .email("socio" + id + "@x.cl")
                .build();
    }

    private void stubReminderWorld(AppUser member, long daysUntilExpiry) {
        when(appUserRepository.findByRoleAndPaidAtIsNotNull(Role.MEMBER)).thenReturn(List.of(member));
        when(gymPlanRepository.findAllById(any())).thenReturn(List.of(plan));
        when(gymRepository.findAllById(any())).thenReturn(List.of(gym));
        // vence dentro de N días calendario (misma hora del día, así la cuenta por fecha es exacta)
        when(memberService.periodEnd(member)).thenReturn(ZonedDateTime.now(ZONE).plusDays(daysUntilExpiry));
    }

    @Test
    void remindsFromThreeDaysBeforeExpiry() {
        AppUser m = member(1);
        stubReminderWorld(m, 3);

        reminderJob.sendReminders();

        verify(emailService).sendMembershipExpiringSoonMember(gym, m, plan, 3L);
    }

    @Test
    void remindsAtTwoAndOneDaysToo() {
        for (long days : new long[] {2, 1}) {
            AppUser m = member(10 + days);
            stubReminderWorld(m, days);

            reminderJob.sendReminders();

            verify(emailService).sendMembershipExpiringSoonMember(gym, m, plan, days);
        }
    }

    @Test
    void doesNotRemindBeforeThreeDays() {
        AppUser m = member(2);
        stubReminderWorld(m, 4);

        reminderJob.sendReminders();

        verify(emailService, never()).sendMembershipExpiringSoonMember(any(), any(), any(), anyLong());
        verify(emailService, never()).sendMembershipExpiredMember(any(), any(), any());
    }

    @Test
    void sendsTheExpiredNoticeOnTheDayItExpires() {
        AppUser m = member(3);
        stubReminderWorld(m, 0);
        when(appUserRepository.findByGymIdAndRole(GYM_ID, Role.GYM_ADMIN)).thenReturn(List.of());

        reminderJob.sendReminders();

        verify(emailService).sendMembershipExpiredMember(gym, m, plan);
        verify(emailService, never()).sendMembershipExpiringSoonMember(any(), any(), any(), anyLong());
    }
}
