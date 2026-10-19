package com.cortesdev.mygym.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymSubscription;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.GymSubscriptionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

/** Avisos y suspension del cron diario de suscripciones. Corte el 15/11: 12-14 avisos, 15 vence hoy, 16-17 gracia, 18 suspende. */
@ExtendWith(MockitoExtension.class)
class GymSubscriptionJobTest {

    private static final Long GYM_ID = 5L;
    private static final LocalDate ANCHOR = LocalDate.of(2026, 10, 15); // 1 mes pagado: vence el 15/11

    @Mock private GymSubscriptionRepository subscriptionRepository;
    @Mock private GymRepository gymRepository;
    @Mock private GymSubscriptionService subscriptionService;
    @Mock private GymBillingNotifier notifier;
    @Mock private TransactionTemplate transactionTemplate;
    @InjectMocks private GymSubscriptionJob job;

    private final Gym gym = Gym.builder().id(GYM_ID).name("XaloGym").active(true).build();
    private GymSubscription sub;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        sub = GymSubscription.builder().gymId(GYM_ID).planId(1L).anchorDate(ANCHOR).paidPeriods(1).billingStatus("ACTIVE").build();
        lenient().when(subscriptionRepository.findByExemptFalse()).thenReturn(List.of(sub));
        lenient().when(subscriptionRepository.findLockedByGymId(GYM_ID)).thenReturn(Optional.of(sub));
        lenient().when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(gym));
        lenient().doAnswer(inv -> {
                    ((Consumer<Object>) inv.getArgument(0)).accept(null);
                    return null;
                })
                .when(transactionTemplate)
                .executeWithoutResult(any());
        // El servicio real recalcula el estado con las fechas; aqui se reproduce para probar solo el cron.
        lenient().when(subscriptionService.refreshStatus(any(), any(), org.mockito.ArgumentMatchers.anyBoolean())).thenAnswer(inv -> {
            GymSubscription s = inv.getArgument(0);
            String after = GymBilling.statusFor(s, inv.getArgument(1));
            boolean changed = !after.equals(s.getBillingStatus());
            s.setBillingStatus(after);
            return changed;
        });
    }

    @Test
    void remindsInTheLastThreeDaysEveryDay() {
        job.run(LocalDate.of(2026, 11, 12));
        verify(notifier).expiring(eq(gym), eq(3L), eq(LocalDate.of(2026, 11, 15)));

        sub.setLastNoticeOn(null);
        job.run(LocalDate.of(2026, 11, 14));
        verify(notifier).expiring(eq(gym), eq(1L), eq(LocalDate.of(2026, 11, 15)));
    }

    @Test
    void doesNotNoticeEarlierThanThreeDaysBefore() {
        job.run(LocalDate.of(2026, 11, 11));

        verify(notifier, never()).expiring(any(), anyLong(), any());
        verify(notifier, never()).dueToday(any(), any());
    }

    @Test
    void warnsOnTheDueDateAndStillKeepsTheGymActive() {
        job.run(LocalDate.of(2026, 11, 15));

        verify(notifier).dueToday(gym, LocalDate.of(2026, 11, 15));
        assertEquals("ACTIVE", sub.getBillingStatus());
    }

    @Test
    void duringTheGraceDaysItCountsDownToTheSuspension() {
        job.run(LocalDate.of(2026, 11, 16));
        verify(notifier).grace(gym, 2L);
        assertEquals("GRACE", sub.getBillingStatus());

        sub.setLastNoticeOn(null);
        job.run(LocalDate.of(2026, 11, 17));
        verify(notifier).grace(gym, 1L);
    }

    @Test
    void suspendsOnTheThirdDayAndNotifiesOnlyTheFirstTime() {
        sub.setBillingStatus("GRACE");
        job.run(LocalDate.of(2026, 11, 18));
        assertEquals("SUSPENDED", sub.getBillingStatus());
        verify(notifier).suspended(gym);

        sub.setLastNoticeOn(null);
        job.run(LocalDate.of(2026, 11, 19)); // sigue suspendido: no se vuelve a avisar
        verify(notifier).suspended(gym); // una sola vez en total
    }

    @Test
    void neverNoticesTwiceTheSameDay() {
        job.run(LocalDate.of(2026, 11, 13));
        job.run(LocalDate.of(2026, 11, 13));

        verify(notifier).expiring(any(), anyLong(), any()); // exactamente una vez
    }

    @Test
    void aGymPausedByHandKeepsItsClockButGetsNoNotices() {
        gym.setActive(false);

        job.run(LocalDate.of(2026, 11, 18));

        assertEquals("SUSPENDED", sub.getBillingStatus());
        verify(notifier, never()).suspended(any());
        verify(notifier, never()).dueToday(any(), any());
    }

    @Test
    void aGymThatWasNeverActivatedIsSkipped() {
        sub.setAnchorDate(null);
        sub.setBillingStatus("NOT_STARTED");

        job.run(LocalDate.of(2026, 11, 18));

        verify(subscriptionService, never()).refreshStatus(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(notifier, never()).suspended(any());
    }
}
