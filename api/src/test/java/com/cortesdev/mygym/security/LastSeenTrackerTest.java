package com.cortesdev.mygym.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.repositories.AppUserRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LastSeenTrackerTest {

    @Mock private AppUserRepository appUserRepository;
    @InjectMocks private LastSeenTracker tracker;

    @Test
    void aGymAdminDoingSomethingIsRecordedButOnlyOncePerHour() {
        tracker.touch(7L, "GYM_ADMIN", "/api/gym-admin/members");
        tracker.touch(7L, "GYM_ADMIN", "/api/gym-admin/classes");
        tracker.touch(7L, "GYM_ADMIN", "/api/gym-admin/members");

        verify(appUserRepository, times(1)).touchLastSeen(eq(7L), any(Instant.class), any(Instant.class));
    }

    @Test
    void eachAdminHasHisOwnClock() {
        tracker.touch(7L, "GYM_ADMIN", "/api/gym-admin/members");
        tracker.touch(8L, "GYM_ADMIN", "/api/gym-admin/members");

        verify(appUserRepository).touchLastSeen(eq(7L), any(), any());
        verify(appUserRepository).touchLastSeen(eq(8L), any(), any());
    }

    @Test
    void theBackgroundStatusPollOfAnOpenTabIsNotActivity() {
        tracker.touch(7L, "GYM_ADMIN", "/api/gym-status");

        verify(appUserRepository, never()).touchLastSeen(any(), any(), any());
    }

    @Test
    void onlyGymAdminsAreTracked() {
        tracker.touch(7L, "MEMBER", "/api/me/dashboard");
        tracker.touch(7L, "PROFESOR", "/api/profesor/x");
        tracker.touch(7L, "SUPER_ADMIN", "/api/gyms");
        tracker.touch(7L, "DEMO_ADMIN", "/api/gym-admin/x");
        tracker.touch(null, "GYM_ADMIN", "/api/gym-admin/x");

        verify(appUserRepository, never()).touchLastSeen(any(), any(), any());
    }

    @Test
    void theThresholdPassedToTheDatabaseIsOneHourBehindNow() {
        org.mockito.ArgumentCaptor<Instant> now = org.mockito.ArgumentCaptor.forClass(Instant.class);
        org.mockito.ArgumentCaptor<Instant> threshold = org.mockito.ArgumentCaptor.forClass(Instant.class);

        tracker.touch(7L, "GYM_ADMIN", "/api/gym-admin/members");

        verify(appUserRepository).touchLastSeen(eq(7L), now.capture(), threshold.capture());
        org.junit.jupiter.api.Assertions.assertEquals(LastSeenTracker.INTERVAL, java.time.Duration.between(threshold.getValue(), now.getValue()));
    }

    @Test
    void aFailingWriteNeverBreaksTheRequestAndIsNotRetriedOnEveryCall() {
        when(appUserRepository.touchLastSeen(any(), any(), any())).thenThrow(new RuntimeException("base caída"));

        tracker.touch(7L, "GYM_ADMIN", "/api/gym-admin/members");
        tracker.touch(7L, "GYM_ADMIN", "/api/gym-admin/members");

        verify(appUserRepository, times(1)).touchLastSeen(any(), any(), any());
    }
}
