package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymSubscription;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.GymSubscriptionRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cron diario (09:00, hora de Chile) de la suscripcion de los gimnasios a mygym: avisa a los administradores 3, 2 y 1 dias
 * antes, el dia del vencimiento y durante la gracia, y suspende al tercer dia. Un gimnasio que el super-admin pauso a mano no
 * recibe avisos (el reloj sigue corriendo). Los exentos se saltan. Cada gimnasio va en su propia transaccion: uno con datos
 * raros nunca frena a los demas.
 */
@Component
@RequiredArgsConstructor
public class GymSubscriptionJob {

    private static final Logger log = LoggerFactory.getLogger(GymSubscriptionJob.class);

    private final GymSubscriptionRepository subscriptionRepository;
    private final GymRepository gymRepository;
    private final GymSubscriptionService subscriptionService;
    private final GymBillingNotifier notifier;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(cron = "0 0 9 * * *", zone = "America/Santiago")
    public void run() {
        run(GymBilling.today());
    }

    void run(LocalDate today) {
        var subs = subscriptionRepository.findByExemptFalse();
        int processed = 0;
        for (GymSubscription candidate : subs) {
            if (candidate.getAnchorDate() == null) {
                continue; // sin iniciar: no hay nada que cobrar todavia
            }
            try {
                // El gimnasio se lee fresco DENTRO de la transaccion de cada uno (no todos de una vez al inicio): asi no se cargan
                // los logos de todos juntos y un cambio reciente del gimnasio no se pisa al guardar el cambio de plan.
                transactionTemplate.executeWithoutResult(tx -> processOne(candidate.getGymId(), today));
                processed++;
            } catch (RuntimeException e) {
                log.error("No se pudo procesar la suscripción del gimnasio {}: {}", candidate.getGymId(), e.getMessage());
            }
        }
        log.info("GymSubscriptionJob: {} suscripciones revisadas", processed);
    }

    private void processOne(Long gymId, LocalDate today) {
        GymSubscription sub = subscriptionRepository.findLockedByGymId(gymId).orElse(null);
        Gym gym = gymRepository.findById(gymId).orElse(null);
        if (sub == null || gym == null || sub.isExempt() || sub.getAnchorDate() == null) {
            return;
        }
        subscriptionService.promotePendingPlanIfDue(sub, gym, today);
        boolean changed = subscriptionService.refreshStatus(sub, today, true);
        if (!gym.isActive() || today.equals(sub.getLastNoticeOn())) {
            return; // pausado a mano o ya avisado hoy
        }
        LocalDate due = GymBilling.dueDate(sub);
        long days = GymBilling.daysToDue(sub, today);
        boolean notified = true;
        switch (sub.getBillingStatus()) {
            case GymSubscription.ACTIVE -> {
                if (days >= 1 && days <= GymBilling.RENEW_WINDOW_DAYS) {
                    notifier.expiring(gym, days, due);
                } else if (days == 0) {
                    notifier.dueToday(gym, due);
                } else {
                    notified = false;
                }
            }
            case GymSubscription.GRACE -> notifier.grace(gym, java.time.temporal.ChronoUnit.DAYS.between(today, GymBilling.suspensionDate(due)));
            case GymSubscription.SUSPENDED -> {
                if (changed) {
                    notifier.suspended(gym);
                } else {
                    notified = false;
                }
            }
            default -> notified = false;
        }
        if (notified) {
            sub.setLastNoticeOn(today);
            subscriptionRepository.save(sub);
        }
    }
}
