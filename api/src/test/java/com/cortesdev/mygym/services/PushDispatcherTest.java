package com.cortesdev.mygym.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.PushSubscription;
import com.cortesdev.mygym.repositories.PushSubscriptionRepository;
import com.cortesdev.mygym.services.PushNotificationService.Delivery;
import com.cortesdev.mygym.services.PushNotificationService.PushRequested;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushDispatcherTest {

    private static final PushMessage MESSAGE = new PushMessage("t", "b", "tag", "/member", 60, PushMessage.Urgency.NORMAL);

    @Mock private PushSubscriptionRepository repository;
    @Mock private PushSender sender;
    @InjectMocks private PushDispatcher dispatcher;

    private PushSubscription subscription;

    @BeforeEach
    void setUp() {
        subscription = PushSubscription.builder().id(1L).userId(7L).endpoint("https://fcm.googleapis.com/x").build();
    }

    private PushRequested requestFor(Long... userIds) {
        return new PushRequested(() -> new Delivery(List.of(userIds), MESSAGE));
    }

    @Test
    void aDeliveredPushRecordsTheSuccessAndResetsFailures() {
        subscription.setFailureCount(3);
        when(sender.isConfigured()).thenReturn(true);
        when(repository.findByUserIdIn(any())).thenReturn(List.of(subscription));
        when(sender.send(subscription, MESSAGE)).thenReturn(PushSender.Result.SENT);

        dispatcher.onPushRequested(requestFor(7L));

        assertEquals(0, subscription.getFailureCount());
        assertNotNull(subscription.getLastSuccessAt());
        verify(repository).save(subscription);
    }

    @Test
    void aSubscriptionThePushServiceReportsAsGoneIsDeleted() {
        when(sender.isConfigured()).thenReturn(true);
        when(repository.findByUserIdIn(any())).thenReturn(List.of(subscription));
        when(sender.send(subscription, MESSAGE)).thenReturn(PushSender.Result.GONE);

        dispatcher.onPushRequested(requestFor(7L));

        verify(repository).delete(subscription);
    }

    @Test
    void aTransientFailureCountsUpAndTenInARowDropTheSubscription() {
        when(sender.isConfigured()).thenReturn(true);
        when(repository.findByUserIdIn(any())).thenReturn(List.of(subscription));
        when(sender.send(subscription, MESSAGE)).thenReturn(PushSender.Result.FAILED);

        dispatcher.onPushRequested(requestFor(7L));
        assertEquals(1, subscription.getFailureCount());
        verify(repository).save(subscription);

        subscription.setFailureCount(9);
        dispatcher.onPushRequested(requestFor(7L));
        verify(repository).delete(subscription);
    }

    @Test
    void withoutVapidKeysNothingIsQueriedOrSent() {
        when(sender.isConfigured()).thenReturn(false);

        dispatcher.onPushRequested(requestFor(7L));

        verify(repository, never()).findByUserIdIn(any());
        verify(sender, never()).send(any(), any());
    }

    @Test
    void aDeliveryComputedAsNullSendsNothing() {
        when(sender.isConfigured()).thenReturn(true);

        dispatcher.onPushRequested(new PushRequested(() -> null));

        verify(repository, never()).findByUserIdIn(any());
    }

    @Test
    void anErrorWhileBuildingTheDeliveryNeverEscapes() {
        when(sender.isConfigured()).thenReturn(true);

        dispatcher.onPushRequested(new PushRequested(() -> {
            throw new IllegalStateException("boom");
        }));

        verify(sender, never()).send(any(), any());
    }
}
