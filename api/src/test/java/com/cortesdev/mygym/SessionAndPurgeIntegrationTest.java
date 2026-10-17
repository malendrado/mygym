package com.cortesdev.mygym;

import static org.assertj.core.api.Assertions.assertThat;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.AuthToken;
import com.cortesdev.mygym.models.AuthTokenPurpose;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.Payment;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.TvCheckinCode;
import com.cortesdev.mygym.models.TvPairingCode;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.AuthTokenRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.PaymentRepository;
import com.cortesdev.mygym.repositories.TvCheckinCodeRepository;
import com.cortesdev.mygym.repositories.TvPairingCodeRepository;
import com.cortesdev.mygym.security.UserSessionGuard;
import com.cortesdev.mygym.services.ExpiredRowsPurgeJob;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lo que las pruebas con Mockito no pueden demostrar: que las consultas nuevas (proyección de la sesión, bloqueo de la
 * fila del pago, borrados de la purga) funcionan de verdad contra Postgres. Auditoría 2026-10-17.
 */
@SpringBootTest
@Transactional
class SessionAndPurgeIntegrationTest {

    @Autowired private AppUserRepository appUserRepository;
    @Autowired private GymRepository gymRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private TvPairingCodeRepository pairingCodeRepository;
    @Autowired private TvCheckinCodeRepository checkinCodeRepository;
    @Autowired private AuthTokenRepository authTokenRepository;
    @Autowired private UserSessionGuard userSessionGuard;
    @Autowired private ExpiredRowsPurgeJob purgeJob;
    @Autowired private EntityManager entityManager;

    private Gym gym() {
        return gymRepository.save(Gym.builder()
                .name("Gym sesión").slug("gym-sesion-" + System.nanoTime()).maxUsers(10).active(true)
                .googleLoginEnabled(true).bookingWindowMinutes(30).cancellationWindowMinutes(30).build());
    }

    @Test
    void theSessionLookupReturnsRoleGymAndActiveFlagFromTheRealTable() {
        Gym gym = gym();
        AppUser admin = appUserRepository.save(AppUser.builder()
                .email("admin-" + System.nanoTime() + "@test.com").name("Admin").role(Role.GYM_ADMIN)
                .gymId(gym.getId()).active(true).build());
        entityManager.flush();

        var view = appUserRepository.findSessionViewById(admin.getId()).orElseThrow();

        assertThat(view.getRole()).isEqualTo(Role.GYM_ADMIN);
        assertThat(view.getGymId()).isEqualTo(gym.getId());
        assertThat(view.getActive()).isTrue();
        assertThat(appUserRepository.findSessionViewById(-1L)).isEmpty();
    }

    @Test
    void removingOrDeactivatingAnAccountCutsItsSessionAsSoAsTheCacheExpires() {
        Gym gym = gym();
        AppUser admin = appUserRepository.save(AppUser.builder()
                .email("admin-" + System.nanoTime() + "@test.com").name("Admin").role(Role.GYM_ADMIN)
                .gymId(gym.getId()).active(true).build());
        entityManager.flush();
        assertThat(userSessionGuard.isValid(admin.getId(), "GYM_ADMIN", gym.getId())).isTrue();
        // El rol del token tiene que seguir siendo el de la fila.
        assertThat(userSessionGuard.isValid(admin.getId(), "SUPER_ADMIN", gym.getId())).isFalse();

        admin.setActive(false);
        appUserRepository.saveAndFlush(admin);
        userSessionGuard.invalidate(admin.getId());
        assertThat(userSessionGuard.isValid(admin.getId(), "GYM_ADMIN", gym.getId())).isFalse();

        appUserRepository.delete(admin);
        appUserRepository.flush();
        userSessionGuard.invalidate(admin.getId());
        assertThat(userSessionGuard.isValid(admin.getId(), "GYM_ADMIN", gym.getId())).isFalse();
    }

    @Test
    void thePaymentRowCanBeLockedByItsFlowToken() {
        Payment payment = paymentRepository.save(Payment.builder()
                .memberId(appUserRepository.save(AppUser.builder()
                                .email("p-" + System.nanoTime() + "@test.com").name("P").role(Role.MEMBER)
                                .gymId(gym().getId()).active(true).build())
                        .getId())
                .planId(null).amountClp(10_000).status("PENDING").flowToken("tok-" + System.nanoTime()).build());
        entityManager.flush();

        assertThat(paymentRepository.findLockedByFlowToken(payment.getFlowToken())).isPresent();
        assertThat(paymentRepository.findLockedByFlowToken("no-existe")).isEmpty();
    }

    @Test
    void theDailyPurgeDeletesOnlyWhatExpiredBeforeEachGracePeriod() {
        Gym gym = gym();
        Instant now = Instant.now();
        Instant longAgo = now.minus(Duration.ofDays(30));
        Instant justExpired = now.minus(Duration.ofHours(2)); // dentro del margen de 1 día: se conserva
        Instant future = now.plus(Duration.ofMinutes(10));

        TvPairingCode oldPairing = pairingCodeRepository.save(TvPairingCode.builder()
                .code("OLD" + System.nanoTime() % 100000).createdAt(longAgo).expiresAt(longAgo).build());
        TvPairingCode recentPairing = pairingCodeRepository.save(TvPairingCode.builder()
                .code("REC" + System.nanoTime() % 100000).createdAt(justExpired).expiresAt(justExpired).build());
        TvCheckinCode oldCheckin = checkinCodeRepository.save(TvCheckinCode.builder()
                .gymId(gym.getId()).code("OC" + System.nanoTime() % 100000).createdAt(longAgo).expiresAt(longAgo).build());
        TvCheckinCode liveCheckin = checkinCodeRepository.save(TvCheckinCode.builder()
                .gymId(gym.getId()).code("LC" + System.nanoTime() % 100000).createdAt(now).expiresAt(future).build());
        AuthToken oldToken = authTokenRepository.save(AuthToken.builder()
                .purpose(AuthTokenPurpose.PASSWORD_RESET).tokenHash("old-" + System.nanoTime()).pendingEmail("a@b.cl")
                .expiresAt(longAgo).build());
        AuthToken liveToken = authTokenRepository.save(AuthToken.builder()
                .purpose(AuthTokenPurpose.PASSWORD_RESET).tokenHash("live-" + System.nanoTime()).pendingEmail("c@d.cl")
                .expiresAt(future).build());
        entityManager.flush();

        purgeJob.purge();
        entityManager.flush();
        entityManager.clear();

        assertThat(pairingCodeRepository.findById(oldPairing.getId())).isEmpty();
        assertThat(pairingCodeRepository.findById(recentPairing.getId())).isPresent();
        assertThat(checkinCodeRepository.findById(oldCheckin.getId())).isEmpty();
        assertThat(checkinCodeRepository.findById(liveCheckin.getId())).isPresent();
        assertThat(authTokenRepository.findById(oldToken.getId())).isEmpty();
        assertThat(authTokenRepository.findById(liveToken.getId())).isPresent();
    }
}
