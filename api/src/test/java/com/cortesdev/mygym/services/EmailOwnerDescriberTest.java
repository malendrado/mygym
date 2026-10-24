package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EmailOwnerDescriberTest {

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final GymRepository gyms = mock(GymRepository.class);
    private final EmailOwnerDescriber describer = new EmailOwnerDescriber(users, gyms);

    private void userWith(Role role, Long gymId) {
        when(users.findByEmail("ana@gym.cl")).thenReturn(Optional.of(AppUser.builder().email("ana@gym.cl").role(role).gymId(gymId).build()));
    }

    @Test
    void saysTheRoleAndTheGymOfAnExistingEmail() {
        userWith(Role.MEMBER, 60L);
        Gym gym = Gym.builder().id(60L).name("XaloGym").build();
        when(gyms.findById(60L)).thenReturn(Optional.of(gym));

        assertThat(describer.describe("Ana@Gym.cl ")).contains("socio del gimnasio «XaloGym» (id 60)");
    }

    @Test
    void anAdminWithoutGymOnlySaysTheRole() {
        userWith(Role.SUPER_ADMIN, null);

        assertThat(describer.describe("ana@gym.cl")).contains("super administrador de mygym");
    }

    @Test
    void anUnknownEmailDescribesNothing() {
        when(users.findByEmail("nadie@gym.cl")).thenReturn(Optional.empty());

        assertThat(describer.describe("nadie@gym.cl")).isEmpty();
    }
}
