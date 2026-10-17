package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymPlan;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymPlanRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cron diario que avisa a un socio cuando le quedan 3 días de membresía — antes esto lo
 * disparaba el propio cliente al notar el vencimiento en su pantalla (member.ts), lo que
 * significaba que si el socio no abría la app, nadie se enteraba. Con pago manual mes a mes
 * (sin cobro automático, ver FlowPaymentService) este aviso es lo único que le recuerda pagar
 * de nuevo antes de quedarse sin poder reservar.
 *
 * Pedido explícito del usuario (2026-10-10, antes eran 2 días): avisar "desde que le queden 3
 * días" — se manda una vez por día mientras falten 3, 2 o 1 días (tres avisos como máximo, uno por
 * día, nunca antes de esa ventana; coincide con el umbral de "Por vencer" de MemberService).
 * El aviso de "ya venció" se dispara aparte, una sola vez, el día que se cumple.
 */
@Component
@RequiredArgsConstructor
public class MembershipReminderJob {

    private static final Logger log = LoggerFactory.getLogger(MembershipReminderJob.class);
    private static final ZoneId GYM_ZONE = ZoneId.of("America/Santiago");
    private static final long REMINDER_DAYS_BEFORE = 3;

    // Ventana del último pago que puede tener un período (pago + 1 mes) que vence en 0 a 3 días: un mes mide 28 a 31 días,
    // así que el pago está entre 31 y 24 días atrás; se suma 1 día de margen a cada lado (hora de Chile vs. instante).
    static final long PAID_AT_WINDOW_FROM_DAYS = 33;
    static final long PAID_AT_WINDOW_TO_DAYS = 23;

    private final AppUserRepository appUserRepository;
    private final MemberService memberService;
    private final GymRepository gymRepository;
    private final GymPlanRepository gymPlanRepository;
    private final MemberLifecycleEmailService memberLifecycleEmailService;
    private final PushNotificationService pushService;

    // Todos los días a las 09:00, hora de Chile — ni tan temprano que nadie lo vea, ni tan
    // tarde que un socio con la clase de la mañana ya se haya quedado sin poder reservar.
    @Scheduled(cron = "0 0 9 * * *", zone = "America/Santiago")
    public void sendReminders() {
        ZonedDateTime reference = ZonedDateTime.now(GYM_ZONE);
        // Solo los socios cuyo período (último pago + 1 mes) vence entre hoy y dentro de 3 días: antes se cargaban todos los que
        // alguna vez pagaron, de todos los gimnasios, cada mañana, solo para descartar casi todos en memoria.
        List<AppUser> members = appUserRepository.findByRoleAndPaidAtBetween(
                Role.MEMBER,
                reference.minusDays(PAID_AT_WINDOW_FROM_DAYS).toInstant(),
                reference.minusDays(PAID_AT_WINDOW_TO_DAYS).toInstant());

        // Antes esto era 1 findById de plan + 1 findById de gym POR SOCIO dentro del loop — con
        // volumen real de socios pagos, cientos de queries secuenciales cada mañana. Se trae todo
        // de una vez con findAllById (auditoría de performance 2026-10-09).
        Map<Long, GymPlan> plansById = gymPlanRepository
                .findAllById(members.stream().map(AppUser::getPlanId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(GymPlan::getId, p -> p));
        Map<Long, Gym> gymsById = gymRepository
                .findAllById(members.stream().map(AppUser::getGymId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Gym::getId, g -> g));
        Map<Long, List<String>> adminEmailsByGymId = new HashMap<>();

        ZonedDateTime now = ZonedDateTime.now(GYM_ZONE);
        int sent = 0;
        for (AppUser member : members) {
            try {
                if (processMember(member, now, plansById, gymsById, adminEmailsByGymId)) {
                    sent++;
                }
            } catch (Exception e) {
                // Un socio con datos raros (plan borrado, gym borrado) nunca debe frenar el
                // aviso del resto — mismo criterio best-effort que el resto de los emails.
                log.error("No se pudo procesar el aviso de vencimiento para el socio {}: {}", member.getId(), e.getMessage());
            }
        }
        log.info("MembershipReminderJob: {} avisos enviados de {} socios con período por vencer", sent, members.size());
    }

    private boolean processMember(
            AppUser member,
            ZonedDateTime now,
            Map<Long, GymPlan> plansById,
            Map<Long, Gym> gymsById,
            Map<Long, List<String>> adminEmailsByGymId) {
        if (member.getPlanId() == null) {
            return false;
        }
        ZonedDateTime periodEnd = memberService.periodEnd(member);
        GymPlan plan = plansById.get(member.getPlanId());
        Gym gym = gymsById.get(member.getGymId());
        if (plan == null || gym == null || !gym.isActive()) {
            return false; // gimnasio en pausa: no se manda ningun aviso automatico
        }

        // Comparación por fecha calendario, NO por Duration entre instantes — con Duration, un
        // desfase de pocos segundos/horas entre el momento en que se pagó y el momento en que
        // corre el job trunca "2 días" a "1 día" (Duration.toDays() redondea hacia abajo). Acá
        // "vence en N días" tiene que coincidir con cómo lo cuenta una persona mirando el
        // calendario, no con las horas exactas transcurridas.
        LocalDate today = today(now);
        LocalDate expiryDate = today(periodEnd);
        long daysUntilExpiry = ChronoUnit.DAYS.between(today, expiryDate);

        if (daysUntilExpiry > 0 && daysUntilExpiry <= REMINDER_DAYS_BEFORE) {
            memberLifecycleEmailService.sendMembershipExpiringSoonMember(gym, member, plan, daysUntilExpiry);
            pushService.notifyUsers(List.of(member.getId()), PushMessages.membershipExpiringSoon(gym, daysUntilExpiry));
            return true;
        }
        if (daysUntilExpiry == 0) {
            List<String> adminEmails = adminEmailsByGymId.computeIfAbsent(
                    gym.getId(),
                    gymId -> appUserRepository.findByGymIdAndRole(gymId, Role.GYM_ADMIN).stream()
                            .map(AppUser::getEmail)
                            .toList());
            boolean alreadyExpired = !now.isBefore(periodEnd);
            memberLifecycleEmailService.sendMembershipExpiredMember(gym, member, plan, alreadyExpired);
            pushService.notifyUsers(List.of(member.getId()), PushMessages.membershipExpired(gym, alreadyExpired));
            memberLifecycleEmailService.sendMembershipExpiredAdmin(gym, member, plan, adminEmails, alreadyExpired);
            return true;
        }
        return false;
    }

    private static LocalDate today(ZonedDateTime value) {
        return value.withZoneSameInstant(GYM_ZONE).toLocalDate();
    }
}
