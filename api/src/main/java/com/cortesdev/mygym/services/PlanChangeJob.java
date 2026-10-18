package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.repositories.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Revisa cada 5 minutos a los socios con un cambio de plan programado y activa el que ya corresponde (ver PlanChangeService). */
@Component
@RequiredArgsConstructor
public class PlanChangeJob {

    private static final Logger log = LoggerFactory.getLogger(PlanChangeJob.class);

    private final AppUserRepository appUserRepository;
    private final PlanChangeService planChangeService;

    @Scheduled(initialDelay = 60_000, fixedDelay = 300_000)
    public void activateDueChanges() {
        for (AppUser member : appUserRepository.findByPendingPlanIdIsNotNull()) {
            try {
                planChangeService.activateIfDue(member.getId());
            } catch (RuntimeException e) {
                // Un socio con problemas no frena a los demás; se reintenta en la próxima vuelta.
                log.error("No se pudo activar el cambio de plan del socio {}", member.getId(), e);
            }
        }
    }
}
