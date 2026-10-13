package com.cortesdev.mygym.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.PushSubscription;
import com.cortesdev.mygym.repositories.PushSubscriptionRepository;
import com.cortesdev.mygym.services.exception.InvalidPushSubscriptionException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushSubscriptionServiceTest {

    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/abc";
    private static final String P256DH = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";

    @Mock private PushSubscriptionRepository repository;
    @Mock private PushNotificationService pushService;
    @Mock private PushSender sender;
    @InjectMocks private PushSubscriptionService service;

    @Test
    void registersANewDeviceForTheUser() {
        when(repository.findByEndpoint(ENDPOINT)).thenReturn(Optional.empty());
        when(repository.findByUserId(7L)).thenReturn(List.of());

        service.register(7L, ENDPOINT, P256DH, AUTH, "Chrome Android");

        ArgumentCaptor<PushSubscription> saved = ArgumentCaptor.forClass(PushSubscription.class);
        verify(repository).save(saved.capture());
        assertEquals(7L, saved.getValue().getUserId());
        assertEquals(ENDPOINT, saved.getValue().getEndpoint());
        assertEquals("Chrome Android", saved.getValue().getUserAgent());
    }

    @Test
    void theSamePhoneUsedByAnotherPersonPassesToTheNewUser() {
        PushSubscription existing = PushSubscription.builder().id(1L).userId(3L).endpoint(ENDPOINT).failureCount(4).build();
        when(repository.findByEndpoint(ENDPOINT)).thenReturn(Optional.of(existing));
        when(repository.findByUserId(7L)).thenReturn(List.of(existing));

        service.register(7L, ENDPOINT, P256DH, AUTH, null);

        assertEquals(7L, existing.getUserId());
        assertEquals(0, existing.getFailureCount());
        verify(repository).save(existing);
    }

    @Test
    void anEndpointOutsideTheKnownPushServicesIsRejected() {
        for (String bad : new String[] {"https://evil.com/x", "http://fcm.googleapis.com/x", "https://169.254.169.254/x", null}) {
            assertThrows(InvalidPushSubscriptionException.class, () -> service.register(7L, bad, P256DH, AUTH, null));
        }
        verify(repository, never()).save(any());
    }

    @Test
    void malformedKeysAreRejected() {
        assertThrows(InvalidPushSubscriptionException.class, () -> service.register(7L, ENDPOINT, "no-es-base64!!", AUTH, null));
        assertThrows(InvalidPushSubscriptionException.class, () -> service.register(7L, ENDPOINT, "AAAA", AUTH, null)); // largo distinto de 65 bytes
        assertThrows(InvalidPushSubscriptionException.class, () -> service.register(7L, ENDPOINT, P256DH, "AAAA", null)); // distinto de 16 bytes
        assertThrows(InvalidPushSubscriptionException.class, () -> service.register(7L, ENDPOINT, P256DH, null, null));
        verify(repository, never()).save(any());
    }

    @Test
    void aUserNeverKeepsMoreThanTenDevices_theOldestAreDropped() {
        when(repository.findByEndpoint(ENDPOINT)).thenReturn(Optional.empty());
        List<PushSubscription> mine = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            mine.add(PushSubscription.builder().id((long) i).userId(7L).createdAt(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i)).build());
        }
        when(repository.findByUserId(7L)).thenReturn(mine);

        service.register(7L, ENDPOINT, P256DH, AUTH, null);

        ArgumentCaptor<PushSubscription> deleted = ArgumentCaptor.forClass(PushSubscription.class);
        verify(repository, times(2)).delete(deleted.capture());
        assertEquals(List.of(0L, 1L), deleted.getAllValues().stream().map(PushSubscription::getId).toList());
    }

    @Test
    void unsubscribingOnlyTouchesTheCallersOwnDevice() {
        service.unregister(7L, ENDPOINT);
        verify(repository).deleteByEndpointAndUserId(ENDPOINT, 7L);
        service.unregister(7L, null);
        verify(repository, times(1)).deleteByEndpointAndUserId(any(), any());
    }

    @Test
    void theTestNotificationIsLimitedToThreePerHourPerUser() {
        assertTrue(service.sendTest(7L));
        assertTrue(service.sendTest(7L));
        assertTrue(service.sendTest(7L));
        assertFalse(service.sendTest(7L));
        assertTrue(service.sendTest(8L)); // otro usuario tiene su propio cupo
        verify(pushService, times(4)).notifyUsers(any(), any(PushMessage.class));
        verify(pushService).notifyUsers(eq(List.of(8L)), any(PushMessage.class));
    }
}
