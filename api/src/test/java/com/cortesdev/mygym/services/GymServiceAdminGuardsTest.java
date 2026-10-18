package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.cortesdev.mygym.services.exception.AdminRemovalNotAllowedException;
import com.cortesdev.mygym.services.exception.MemberNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Auditoría 2026-10-17: un administrador no puede quitarse a sí mismo, y pagos/planes solo se asignan a SOCIOS. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GymServiceAdminGuardsTest {

    private static final Long GYM_ID = 4L;
    private static final Long ACTOR_ID = 10L;
    private static final Long OTHER_ADMIN_ID = 11L;
    private static final Long STAFF_ID = 12L;
    private static final Long PLAN_ID = 20L;

    @Mock private GymRepository gymRepository;
    @Mock private GymPlanRepository gymPlanRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private PlanChangeService planChangeService;
    @InjectMocks private GymService gymService;

    @BeforeEach
    void gymPlanAndStaff() {
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(Gym.builder().id(GYM_ID).build()));
        when(gymRepository.existsById(GYM_ID)).thenReturn(true);
        when(gymPlanRepository.findByIdAndGymId(PLAN_ID, GYM_ID))
                .thenReturn(Optional.of(GymPlan.builder().id(PLAN_ID).gymId(GYM_ID).name("Plan").priceClp(30_000).build()));
        when(appUserRepository.findByIdAndGymId(STAFF_ID, GYM_ID))
                .thenReturn(Optional.of(AppUser.builder().id(STAFF_ID).gymId(GYM_ID).role(Role.PROFESOR).build()));
        when(appUserRepository.findLockedByIdAndGymId(STAFF_ID, GYM_ID))
                .thenReturn(Optional.of(AppUser.builder().id(STAFF_ID).gymId(GYM_ID).role(Role.PROFESOR).build()));
        when(appUserRepository.findByIdAndGymId(OTHER_ADMIN_ID, GYM_ID))
                .thenReturn(Optional.of(AppUser.builder().id(OTHER_ADMIN_ID).gymId(GYM_ID).role(Role.GYM_ADMIN).build()));
    }

    // --- quitar administradores ---

    @Test
    void anAdminCannotRemoveTheirOwnAccess() {
        assertThatThrownBy(() -> gymService.removeAdminAsPeer(GYM_ID, ACTOR_ID, ACTOR_ID))
                .isInstanceOf(AdminRemovalNotAllowedException.class);

        verify(appUserRepository, never()).delete(any(AppUser.class));
    }

    @Test
    void anAdminCanRemoveAnotherAdminOfTheirOwnGym() {
        gymService.removeAdminAsPeer(GYM_ID, OTHER_ADMIN_ID, ACTOR_ID);

        verify(appUserRepository).delete(any(AppUser.class));
    }

    // --- planes y pagos: solo socios ---

    @Test
    void markingPaidToANonMemberIsRefused() {
        assertThatThrownBy(() -> gymService.simulatePlanPayment(GYM_ID, STAFF_ID, PLAN_ID, "Banco Estado", ACTOR_ID))
                .isInstanceOf(MemberNotFoundException.class);

        verify(appUserRepository, never()).save(any(AppUser.class));
    }

    @Test
    void previewingAPaymentForANonMemberIsRefused() {
        assertThatThrownBy(() -> gymService.previewPlanPayment(GYM_ID, STAFF_ID, PLAN_ID, null, null))
                .isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    void revokingThePlanOfANonMemberIsRefused() {
        assertThatThrownBy(() -> gymService.revokePlan(GYM_ID, STAFF_ID)).isInstanceOf(MemberNotFoundException.class);

        verify(appUserRepository, never()).save(any(AppUser.class));
    }
}
