package com.cortesdev.mygym.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.GymLimitsResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.GymSubscriptionRepository;
import com.cortesdev.mygym.services.exception.GymLimitReachedException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Cupo de socios (Gym.maxUsers), 10 profesores y 5 administradores por gimnasio. */
@ExtendWith(MockitoExtension.class)
class GymCapacityServiceTest {

    private static final Long GYM_ID = 5L;

    @Mock private AppUserRepository appUserRepository;
    @Mock private GymRepository gymRepository;
    @Mock private GymSubscriptionRepository subscriptionRepository;
    @Mock private MemberLifecycleEmailService emailService;
    @org.mockito.Spy private com.cortesdev.mygym.services.MygymSettingsService mygymSettings = com.cortesdev.mygym.services.TestMygymSettings.defaults();
    @InjectMocks private GymCapacityService service;

    private Gym gym(int maxUsers) {
        return Gym.builder().id(GYM_ID).name("XaloGym").maxUsers(maxUsers).build();
    }

    private void members(long used) {
        when(appUserRepository.countByGymIdAndRole(GYM_ID, Role.MEMBER)).thenReturn(used);
    }

    @Test
    void theTeachersAndAdminsCapComeFromTheGymsMygymPlanWhenItHasOne() {
        when(gymRepository.findMaxUsersById(GYM_ID)).thenReturn(Optional.of(50));
        when(subscriptionRepository.findMaxProfesoresByGymId(GYM_ID)).thenReturn(Optional.of(20));
        when(subscriptionRepository.findMaxAdminsByGymId(GYM_ID)).thenReturn(Optional.of(8));
        members(1);
        when(appUserRepository.countByGymIdAndRole(GYM_ID, Role.PROFESOR)).thenReturn(12L);
        when(appUserRepository.countByGymIdAndRole(GYM_ID, Role.GYM_ADMIN)).thenReturn(5L);

        GymLimitsResponse limits = service.limits(GYM_ID);

        assertEquals(new GymLimitsResponse.Usage(12, 20, 8), limits.profesores());
        assertEquals(new GymLimitsResponse.Usage(5, 8, 3), limits.admins());
    }

    @Test
    void limitsReportUsedMaxAndFreeForEveryRole() {
        when(gymRepository.findMaxUsersById(GYM_ID)).thenReturn(Optional.of(50));
        members(47);
        when(appUserRepository.countByGymIdAndRole(GYM_ID, Role.PROFESOR)).thenReturn(3L);
        when(appUserRepository.countByGymIdAndRole(GYM_ID, Role.GYM_ADMIN)).thenReturn(2L);

        GymLimitsResponse limits = service.limits(GYM_ID);

        assertEquals(new GymLimitsResponse.Usage(47, 50, 3), limits.members());
        assertEquals(new GymLimitsResponse.Usage(3, 10, 7), limits.profesores());
        assertEquals(new GymLimitsResponse.Usage(2, 5, 3), limits.admins());
    }

    @Test
    void freeSlotsNeverGoNegativeWhenTheCapWasLoweredBelowTheUsage() {
        members(12);
        assertEquals(0, service.freeMemberSlots(gym(10)));
        when(gymRepository.findMaxUsersById(GYM_ID)).thenReturn(Optional.of(10));
        when(appUserRepository.countByGymIdAndRole(GYM_ID, Role.PROFESOR)).thenReturn(0L);
        when(appUserRepository.countByGymIdAndRole(GYM_ID, Role.GYM_ADMIN)).thenReturn(0L);
        assertEquals(new GymLimitsResponse.Usage(12, 10, 0), service.limits(GYM_ID).members());
    }

    @Test
    void theTeamCanAddMembersWhileThereIsRoomAndIsStoppedWhenItWouldOverflow() {
        members(8);
        service.assertMemberCapacity(gym(10), 2); // justo cabe

        GymLimitReachedException ex = assertThrows(GymLimitReachedException.class, () -> service.assertMemberCapacity(gym(10), 3));
        assertEquals(GymLimitReachedException.Kind.MEMBERS, ex.kind());
        assertTrue(ex.getMessage().contains("límite de 10 socios"), ex.getMessage());
    }

    @Test
    void selfRegistrationWithRoomPassesWithoutNotifyingAnyone() {
        members(9);
        service.assertSelfRegistrationAllowed(gym(10));
        verify(emailService, never()).sendMemberLimitReachedAdmin(any(), any(), anyInt());
    }

    @Test
    void selfRegistrationWithoutRoomExplainsKindlyAndTellsTheAdminOnlyOncePerDay() {
        members(10);
        when(appUserRepository.findByGymIdAndRole(GYM_ID, Role.GYM_ADMIN))
                .thenReturn(List.of(
                        AppUser.builder().id(1L).email("dueno@x.cl").active(true).role(Role.GYM_ADMIN).build(),
                        AppUser.builder().id(2L).email("baja@x.cl").active(false).role(Role.GYM_ADMIN).build()));

        GymLimitReachedException first = assertThrows(GymLimitReachedException.class, () -> service.assertSelfRegistrationAllowed(gym(10)));
        assertThrows(GymLimitReachedException.class, () -> service.assertSelfRegistrationAllowed(gym(10)));
        assertThrows(GymLimitReachedException.class, () -> service.assertSelfRegistrationAllowed(gym(10)));

        assertTrue(first.getMessage().contains("no tiene cupos disponibles"), first.getMessage());
        assertTrue(first.getMessage().contains("Avísale a tu gimnasio"), first.getMessage());
        // Tres intentos el mismo dia = un solo correo, y solo al admin activo.
        verify(emailService, times(1)).sendMemberLimitReachedAdmin(any(Gym.class), eq(List.of("dueno@x.cl")), eq(10));
    }

    @Test
    void aFailingEmailNeverBreaksTheRegistrationAnswer() {
        members(10);
        when(appUserRepository.findByGymIdAndRole(GYM_ID, Role.GYM_ADMIN))
                .thenReturn(List.of(AppUser.builder().id(1L).email("d@x.cl").active(true).build()));
        org.mockito.Mockito.doThrow(new IllegalStateException("resend caído"))
                .when(emailService).sendMemberLimitReachedAdmin(any(), any(), anyInt());

        // Sigue siendo la excepcion de cupo (409 para la persona), no un 500 por el correo.
        assertThrows(GymLimitReachedException.class, () -> service.assertSelfRegistrationAllowed(gym(10)));
    }

    @Test
    void teachersAreCappedAtTenAndAdminsAtFive() {
        when(appUserRepository.countByGymIdAndRole(GYM_ID, Role.PROFESOR)).thenReturn(9L, 10L);
        service.assertProfesorCapacity(GYM_ID);
        GymLimitReachedException teachers = assertThrows(GymLimitReachedException.class, () -> service.assertProfesorCapacity(GYM_ID));
        assertEquals(GymLimitReachedException.Kind.PROFESORES, teachers.kind());
        assertTrue(teachers.getMessage().contains("máximo de 10 profesores"));

        when(appUserRepository.countByGymIdAndRole(GYM_ID, Role.GYM_ADMIN)).thenReturn(4L, 5L);
        service.assertAdminCapacity(GYM_ID);
        GymLimitReachedException admins = assertThrows(GymLimitReachedException.class, () -> service.assertAdminCapacity(GYM_ID));
        assertEquals(GymLimitReachedException.Kind.ADMINS, admins.kind());
        assertTrue(admins.getMessage().contains("máximo de 5 administradores"));
    }
}
