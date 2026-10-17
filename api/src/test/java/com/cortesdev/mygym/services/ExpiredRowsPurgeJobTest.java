package com.cortesdev.mygym.services;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.cortesdev.mygym.repositories.AuthTokenRepository;
import com.cortesdev.mygym.repositories.TvCheckinCodeRepository;
import com.cortesdev.mygym.repositories.TvPairingCodeRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Códigos de TV y tokens de acceso vencidos se borran cada día; antes se acumulaban para siempre (auditoría 2026-10-17). */
@ExtendWith(MockitoExtension.class)
class ExpiredRowsPurgeJobTest {

    @Mock private TvPairingCodeRepository pairingCodeRepository;
    @Mock private TvCheckinCodeRepository checkinCodeRepository;
    @Mock private AuthTokenRepository authTokenRepository;
    @InjectMocks private ExpiredRowsPurgeJob job;

    @Test
    void deletesOnlyWhatExpiredBeforeEachGracePeriod() {
        Instant now = Instant.parse("2026-10-17T12:00:00Z");

        job.purgeAt(now);

        verify(pairingCodeRepository).deleteByExpiresAtBefore(now.minus(ExpiredRowsPurgeJob.CODES_GRACE));
        verify(checkinCodeRepository).deleteByExpiresAtBefore(now.minus(ExpiredRowsPurgeJob.CODES_GRACE));
        verify(authTokenRepository).deleteByExpiresAtBefore(now.minus(ExpiredRowsPurgeJob.TOKENS_GRACE));
        verifyNoMoreInteractions(pairingCodeRepository, checkinCodeRepository, authTokenRepository);
    }
}
