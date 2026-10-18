package com.cortesdev.mygym.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.cortesdev.mygym.repositories.AuthTokenRepository;
import com.cortesdev.mygym.repositories.PageViewRepository;
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
    @Mock private PageViewRepository pageViewRepository;
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

    @Test
    void pageViewsOlderThanThirteenMonthsAreDeletedInBatchesUntilALastShortOne() {
        Instant now = Instant.parse("2026-10-17T12:00:00Z");
        Instant cutoff = Instant.parse("2025-09-17T12:00:00Z");
        int batch = ExpiredRowsPurgeJob.PAGE_VIEW_BATCH_SIZE;
        when(pageViewRepository.deleteOlderThanBatch(cutoff, batch)).thenReturn(batch, batch, 10);

        job.purgeOldPageViews(now);

        verify(pageViewRepository, times(3)).deleteOlderThanBatch(cutoff, batch);
        verifyNoMoreInteractions(pageViewRepository);
    }

    @Test
    void pageViewPurgeStopsAtTheBatchCapEvenIfRowsKeepComing() {
        when(pageViewRepository.deleteOlderThanBatch(any(), anyInt())).thenReturn(ExpiredRowsPurgeJob.PAGE_VIEW_BATCH_SIZE);

        job.purgeOldPageViews(Instant.parse("2026-10-17T12:00:00Z"));

        verify(pageViewRepository, times(ExpiredRowsPurgeJob.PAGE_VIEW_MAX_BATCHES))
                .deleteOlderThanBatch(any(), eq(ExpiredRowsPurgeJob.PAGE_VIEW_BATCH_SIZE));
    }
}
