package com.cortesdev.mygym.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import java.time.DayOfWeek;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GymRepositoryTest {

    @Autowired
    private GymRepository gymRepository;

    @Autowired
    private GymBlockRepository gymBlockRepository;

    @Test
    void findBySlug_returnsMatchingGym() {
        gymRepository.save(newGym("Gold Gym", "gold-gym"));

        assertThat(gymRepository.findBySlug("gold-gym")).isPresent();
        assertThat(gymRepository.findBySlug("does-not-exist")).isEmpty();
    }

    @Test
    void existsBySlug_reflectsPersistedSlugs() {
        gymRepository.save(newGym("Gold Gym", "gold-gym"));

        assertThat(gymRepository.existsBySlug("gold-gym")).isTrue();
        assertThat(gymRepository.existsBySlug("other-gym")).isFalse();
    }

    @Test
    void findByGymId_isIsolatedPerGym() {
        Gym gymA = gymRepository.save(newGym("Gym A", "gym-a"));
        Gym gymB = gymRepository.save(newGym("Gym B", "gym-b"));

        gymBlockRepository.save(newBlock(gymA.getId(), "Morning"));
        gymBlockRepository.save(newBlock(gymB.getId(), "Evening"));

        assertThat(gymBlockRepository.findByGymId(gymA.getId())).hasSize(1);
        assertThat(gymBlockRepository.findByGymId(gymA.getId()).getFirst().getLabel())
                .isEqualTo("Morning");
        assertThat(gymBlockRepository.findByGymId(gymB.getId())).hasSize(1);
    }

    private Gym newGym(String name, String slug) {
        return Gym.builder().name(name).slug(slug).maxUsers(50).active(true).googleLoginEnabled(false).build();
    }

    private GymBlock newBlock(Long gymId, String label) {
        return GymBlock.builder()
                .gymId(gymId)
                .label(label)
                .dayOfWeek(DayOfWeek.MONDAY)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(9, 0))
                .capacity(20)
                .active(true)
                .build();
    }
}
