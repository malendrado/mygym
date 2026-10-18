package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.AdminCreateRequest;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.services.exception.DuplicateOwnerEmailException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** El mensaje de "email duplicado" explica el caso útil (ya es admin/profesor de ESTE gimnasio) y no revela nada de otros. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GymServiceDuplicateStaffEmailTest {

    private static final Long GYM_ID = 4L;
    private static final Long OTHER_GYM_ID = 9L;
    private static final String EMAIL = "Dueno@Gym.cl";

    @Mock private GymRepository gymRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private GymCapacityService gymCapacityService;
    @Mock private PlanChangeService planChangeService;
    @InjectMocks private GymService gymService;

    @BeforeEach
    void gymExists() {
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(Gym.builder().id(GYM_ID).build()));
        when(gymRepository.existsById(GYM_ID)).thenReturn(true);
    }

    private void existingAccount(Role role, Long gymId) {
        when(appUserRepository.findByEmail("dueno@gym.cl"))
                .thenReturn(Optional.of(AppUser.builder().id(1L).email("dueno@gym.cl").role(role).gymId(gymId).build()));
    }

    private AdminCreateRequest request() {
        return new AdminCreateRequest("Dueño", EMAIL);
    }

    @Test
    void addingAsProfesorSomeoneWhoAlreadyAdministersThisGymExplainsAnAdminAlreadyDoesEverything() {
        existingAccount(Role.GYM_ADMIN, GYM_ID);

        assertThatThrownBy(() -> gymService.addProfesor(GYM_ID, request()))
                .isInstanceOf(DuplicateOwnerEmailException.class)
                .hasMessageContaining("ya es administrador de tu gimnasio")
                .hasMessageContaining("ya puede hacer todo lo que hace un profesor");
    }

    @Test
    void addingAsAdminSomeoneWhoIsAlreadyProfesorOfThisGymSaysToRemoveThemFirst() {
        existingAccount(Role.PROFESOR, GYM_ID);

        assertThatThrownBy(() -> gymService.addAdmin(GYM_ID, request()))
                .isInstanceOf(DuplicateOwnerEmailException.class)
                .hasMessageContaining("ya es profesor de tu gimnasio")
                .hasMessageContaining("primero quítalo de los profesores");
    }

    @Test
    void anAccountOfAnotherGymGetsTheGenericMessageAndRevealsNothingMore() {
        existingAccount(Role.GYM_ADMIN, OTHER_GYM_ID);

        assertThatThrownBy(() -> gymService.addProfesor(GYM_ID, request()))
                .isInstanceOf(DuplicateOwnerEmailException.class)
                .hasMessageContaining("ya tiene una cuenta en mygym")
                .hasMessageNotContaining("administrador de tu gimnasio");
    }

    @Test
    void aMemberEmailKeepsTheGenericMessage() {
        existingAccount(Role.MEMBER, GYM_ID);

        assertThatThrownBy(() -> gymService.addProfesor(GYM_ID, request()))
                .isInstanceOf(DuplicateOwnerEmailException.class)
                .hasMessageContaining("ya tiene una cuenta en mygym");
    }

    @Test
    void aFreeEmailIsStillAddedAsProfesor() {
        when(appUserRepository.findByEmail("dueno@gym.cl")).thenReturn(Optional.empty());
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> {
            AppUser saved = inv.getArgument(0);
            saved.setId(77L);
            return saved;
        });

        var response = gymService.addProfesor(GYM_ID, request());

        assertThat(response.email()).isEqualToIgnoringCase(EMAIL);
        verify(appUserRepository).save(any(AppUser.class));
    }
}
