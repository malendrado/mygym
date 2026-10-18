package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymPlan;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymPlanRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cambio de plan programado: un socio con plan vigente que paga OTRO plan no lo pisa; el pago queda en espera
 * (AppUser.pendingPlanId) y el plan nuevo parte cuando se acaben las clases del plan actual o llegue su vencimiento, lo que
 * ocurra primero. Así no pierde los meses que ya traía pagados por adelantado ni las clases que le quedaban.
 *
 * "Se acabaron las clases" = ya USÓ todas las del período (reservas de días anteriores a hoy, más lo declarado al importar):
 * una clase reservada para mañana todavía no se gastó. Solo cuenta si ya está en el último mes pagado (sin cola). Un pago con
 * fecha elegida por el admin o clases declaradas NO se programa: es un reinicio explícito (ver GymService.simulatePlanPayment).
 */
@Service
@RequiredArgsConstructor
public class PlanChangeService {

    private static final Logger log = LoggerFactory.getLogger(PlanChangeService.class);

    private final AppUserRepository appUserRepository;
    private final GymPlanRepository gymPlanRepository;
    private final GymRepository gymRepository;
    private final ReservationRepository reservationRepository;
    private final MemberLifecycleEmailService memberLifecycleEmailService;
    private final ApplicationEventPublisher eventPublisher;

    /** ¿Este pago (de ese plan, sin fecha explícita) queda programado en vez de arrancar de inmediato? */
    public boolean shouldSchedule(AppUser member, Long planId, Instant now) {
        return MembershipPeriods.isPlanChange(member, planId, now) && !isCurrentPlanFinished(member, now);
    }

    /** ¿El plan actual ya no sirve: venció, o está en su último mes y ya usó todas las clases? Sin plan o sin pago: sí. */
    public boolean isCurrentPlanFinished(AppUser member, Instant now) {
        ZonedDateTime zoned = now.atZone(MembershipPeriods.GYM_ZONE);
        if (member.getPlanId() == null || member.getPaidAt() == null || MembershipPeriods.isExpired(member, zoned)) {
            return true;
        }
        if (MembershipPeriods.hasQueued(member, zoned)) {
            return false;
        }
        GymPlan plan = gymPlanRepository.findById(member.getPlanId()).orElse(null);
        if (plan == null || plan.getMonthlyClasses() == null) {
            return false;
        }
        LocalDate start = MembershipPeriods.currentStart(member, zoned).toLocalDate();
        LocalDate yesterday = zoned.toLocalDate().minusDays(1);
        int used = yesterday.isBefore(start)
                ? 0
                : reservationRepository.countByMemberIdAndStatusAndClassDateBetween(
                        member.getId(), ReservationStatus.BOOKED, start, yesterday);
        int usedAtImport = MembershipPeriods.currentIndex(member, zoned) == 1 && member.getUsedSessionsAtImport() != null
                ? member.getUsedSessionsAtImport()
                : 0;
        return used + usedAtImport >= plan.getMonthlyClasses();
    }

    /** Activa el cambio pendiente de ese socio si el plan actual ya terminó. Idempotente; lo recorre PlanChangeJob. */
    @Transactional
    public void activateIfDue(Long memberId) {
        Long gymId = appUserRepository.findGymIdById(memberId).orElse(null);
        if (gymId == null) {
            return;
        }
        // Fila bloqueada: un pago en paralelo del mismo socio se serializa con esta activación.
        AppUser member = appUserRepository.findLockedByIdAndGymId(memberId, gymId).orElse(null);
        if (member == null || member.getPendingPlanId() == null) {
            return;
        }
        Instant now = Instant.now();
        if (!isCurrentPlanFinished(member, now)) {
            return;
        }
        int periods = MembershipPeriods.pendingPeriods(member);
        GymPlan plan = gymPlanRepository
                .findById(member.getPendingPlanId())
                .filter(p -> gymId.equals(p.getGymId()))
                .orElse(null);
        if (plan == null) {
            log.warn("El plan pendiente {} del socio {} ya no existe: se descarta el cambio", member.getPendingPlanId(), memberId);
            member.setPendingPlanId(null);
            member.setPendingPlanPeriods(null);
            appUserRepository.save(member);
            return;
        }
        // Si el plan actual terminó por fecha, el nuevo parte justo en ese vencimiento (sin hueco), siempre que no haya
        // pasado ya todo lo que pagó; si terminó por clases, parte hoy.
        Instant start = now;
        ZonedDateTime end = MembershipPeriods.finalEnd(member);
        if (end != null && MembershipPeriods.isExpired(member, now.atZone(MembershipPeriods.GYM_ZONE))
                && end.plusMonths(periods).toInstant().isAfter(now)) {
            start = end.toInstant();
        }
        MembershipPeriods.startFreshChain(member, plan.getId(), start);
        member.setPendingPlanId(null);
        member.setPendingPlanPeriods(null);
        member.setPaidPeriods(periods > 1 ? periods : null);
        member.setUsedSessionsAtImport(null);
        appUserRepository.save(member);

        Gym gym = gymRepository.findById(gymId).orElse(null);
        if (gym != null) {
            memberLifecycleEmailService.sendPlanChangeActivated(
                    gym, member, plan, start.atZone(MembershipPeriods.GYM_ZONE), MembershipPeriods.finalEnd(member));
        }
        log.info("Cambio de plan activado para el socio {} (plan {}, {} mes(es))", memberId, plan.getId(), periods);
        // Reserva sus clases fijas del plan nuevo, después del commit (ver FixedClassService).
        eventPublisher.publishEvent(new MembershipPaidEvent(memberId));
    }
}
