package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.GymClosure;
import com.cortesdev.mygym.models.GymPlan;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymClosureRepository;
import com.cortesdev.mygym.repositories.GymPlanRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manda los emails de un cierre YA CONFIRMADO, fuera de la transacción que canceló las reservas
 * (ver GymClosureService.create, afterCommit) — mandar un email por socio afectado dentro de esa
 * misma transacción dejaría una conexión del pool de 5 tomada todo el tiempo que tarde el loop
 * completo. Corre en un executor propio (ver AsyncConfig) para no competir por hilos con
 * requests normales.
 */
@Service
@RequiredArgsConstructor
public class ClosureNotificationService {

    private static final Logger log = LoggerFactory.getLogger(ClosureNotificationService.class);
    private static final DateTimeFormatter CLASS_DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", new Locale("es", "CL"));

    private final GymClosureRepository gymClosureRepository;
    private final GymRepository gymRepository;
    private final ReservationRepository reservationRepository;
    private final AppUserRepository appUserRepository;
    private final GymBlockRepository gymBlockRepository;
    private final GymPlanRepository gymPlanRepository;
    private final MemberLifecycleEmailService emailService;

    @Async("closureNotificationExecutor")
    @Transactional
    public void notify(Long closureId, Role createdByRole) {
        GymClosure closure = gymClosureRepository.findById(closureId).orElse(null);
        if (closure == null) {
            return;
        }
        Gym gym = gymRepository.findById(closure.getGymId()).orElse(null);
        if (gym == null) {
            return;
        }

        List<Reservation> cancelled = reservationRepository.findByCancelledByClosureId(closureId);
        Map<Long, List<Reservation>> byMember =
                cancelled.stream().collect(Collectors.groupingBy(Reservation::getMemberId));
        List<Long> blockIds = cancelled.stream().map(Reservation::getGymBlockId).distinct().toList();
        Map<Long, GymBlock> blocksById = blockIds.isEmpty()
                ? Map.of()
                : gymBlockRepository.findAllById(blockIds).stream()
                        .collect(Collectors.toMap(GymBlock::getId, b -> b));

        EmailResult result = sendCancellationEmails(gym, closure, byMember, blocksById, "creación");
        int sent = result.sent();
        int failed = result.failed();

        if (createdByRole == Role.SUPER_ADMIN) {
            List<String> adminEmails = appUserRepository.findByGymIdAndRole(gym.getId(), Role.GYM_ADMIN).stream()
                    .filter(AppUser::isActive)
                    .map(AppUser::getEmail)
                    .toList();
            if (!adminEmails.isEmpty()) {
                try {
                    emailService.sendClosureNoticeAdmin(
                            gym, closure.getReason(), closure.getStartDate(), closure.getEndDate(), adminEmails);
                } catch (Exception e) {
                    log.error("Falló el aviso de cierre al admin del gym {}: {}", gym.getId(), e.getMessage());
                }
            }
        }

        closure.setEmailsSent(sent);
        closure.setEmailsFailed(failed);
        gymClosureRepository.save(closure);
    }

    /**
     * Cierre ALARGADO (ver GymClosureService.update) — a diferencia de {@link #notify}, acá NUNCA
     * se re-consulta todo lo cancelado por el cierre: solo se avisa a los socios de las reservas
     * puntuales que esta edición acaba de cancelar, nunca a quien ya había sido notificado en la
     * creación original (evita mandar el mismo email dos veces). Tampoco avisa al admin del gym
     * — eso solo pasa una vez, en la creación.
     */
    @Async("closureNotificationExecutor")
    @Transactional
    public void notifyIncremental(Long closureId, List<Long> reservationIds) {
        GymClosure closure = gymClosureRepository.findById(closureId).orElse(null);
        if (closure == null || reservationIds.isEmpty()) {
            return;
        }
        Gym gym = gymRepository.findById(closure.getGymId()).orElse(null);
        if (gym == null) {
            return;
        }

        List<Reservation> cancelled = reservationRepository.findAllById(reservationIds);
        Map<Long, List<Reservation>> byMember =
                cancelled.stream().collect(Collectors.groupingBy(Reservation::getMemberId));
        List<Long> blockIds = cancelled.stream().map(Reservation::getGymBlockId).distinct().toList();
        Map<Long, GymBlock> blocksById = blockIds.isEmpty()
                ? Map.of()
                : gymBlockRepository.findAllById(blockIds).stream()
                        .collect(Collectors.toMap(GymBlock::getId, b -> b));

        EmailResult result = sendCancellationEmails(gym, closure, byMember, blocksById, "alargado");

        closure.setEmailsSent(closure.getEmailsSent() + result.sent());
        closure.setEmailsFailed(closure.getEmailsFailed() + result.failed());
        gymClosureRepository.save(closure);
    }

    private record EmailResult(int sent, int failed) {}

    /** Compartido por notify()/notifyIncremental() — antes cada uno hacía un findById de socio Y
     *  un findById de plan POR SOCIO dentro del loop (2×N queries); para un cierre que afecta a
     *  decenas de socios eran decenas de queries secuenciales dentro de una transacción async que
     *  además manda N emails. Ahora socios y planes se traen una sola vez con findAllById antes
     *  del loop (auditoría de performance 2026-10-09). */
    private EmailResult sendCancellationEmails(
            Gym gym, GymClosure closure, Map<Long, List<Reservation>> byMember, Map<Long, GymBlock> blocksById, String logContext) {
        Map<Long, AppUser> membersById = appUserRepository.findAllById(byMember.keySet()).stream()
                .collect(Collectors.toMap(AppUser::getId, m -> m));
        Set<Long> planIds =
                membersById.values().stream().map(AppUser::getPlanId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, GymPlan> plansById = planIds.isEmpty()
                ? Map.of()
                : gymPlanRepository.findAllById(planIds).stream().collect(Collectors.toMap(GymPlan::getId, p -> p));

        int sent = 0;
        int failed = 0;
        for (Map.Entry<Long, List<Reservation>> entry : byMember.entrySet()) {
            AppUser member = membersById.get(entry.getKey());
            if (member == null) {
                failed++;
                continue;
            }
            List<String> labels = entry.getValue().stream()
                    .sorted(Comparator.comparing(Reservation::getClassDate))
                    .map(r -> {
                        GymBlock block = blocksById.get(r.getGymBlockId());
                        String label = block != null ? block.getLabel() : "tu clase";
                        return label + " — " + CLASS_DATE_FORMAT.format(r.getClassDate());
                    })
                    .toList();
            GymPlan plan = member.getPlanId() == null ? null : plansById.get(member.getPlanId());
            boolean quotaRefunded = plan != null && plan.getMonthlyClasses() != null;
            try {
                emailService.sendClosureCancellationMember(gym, member, closure.getReason(), labels, quotaRefunded);
                sent++;
            } catch (Exception e) {
                log.error("Falló el email de cierre ({}) para el socio {}: {}", logContext, member.getId(), e.getMessage());
                failed++;
            }
        }
        return new EmailResult(sent, failed);
    }
}
