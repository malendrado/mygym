package com.cortesdev.mygym;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.repositories.AppUserRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** La anotación de última actividad contra Postgres de verdad: condicional, sin tocar updated_at y sin escribir de más. */
@SpringBootTest
class LastSeenIntegrationTest {

    @Autowired private AppUserRepository appUserRepository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void theUpdateIsConditionalAndLeavesUpdatedAtAlone() {
        AppUser admin = appUserRepository.save(AppUser.builder()
                .email("admin-visto-" + System.nanoTime() + "@test.com").name("Admin").role(Role.GYM_ADMIN).active(true).build());
        try {
            assertNull(jdbc.queryForObject("select last_seen_at from app_user where id = ?", Instant.class, admin.getId()));
            Instant updatedBefore = jdbc.queryForObject("select updated_at from app_user where id = ?", java.sql.Timestamp.class, admin.getId()).toInstant();

            Instant now = Instant.now();
            Instant threshold = now.minusSeconds(3600);
            assertEquals(1, appUserRepository.touchLastSeen(admin.getId(), now, threshold), "la primera vez se anota");
            assertEquals(0, appUserRepository.touchLastSeen(admin.getId(), now.plusSeconds(60), threshold), "dentro de la hora no escribe");
            assertEquals(1, appUserRepository.touchLastSeen(admin.getId(), now.plusSeconds(7200), now.plusSeconds(3600)), "pasada la hora vuelve a anotar");

            assertNotNull(jdbc.queryForObject("select last_seen_at from app_user where id = ?", Instant.class, admin.getId()));
            Instant updatedAfter = jdbc.queryForObject("select updated_at from app_user where id = ?", java.sql.Timestamp.class, admin.getId()).toInstant();
            assertEquals(updatedBefore, updatedAfter, "updated_at no debe moverse: lo usan otras reglas");
        } finally {
            appUserRepository.deleteById(admin.getId());
        }
    }
}
