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
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cron diario que avisa a un socio cuando le quedan 2 días de membresía — antes esto lo
 * disparaba el propio cliente al notar el vencimiento en su pantalla (member.ts), lo que
 * significaba que si el socio no abría la app, nadie se enteraba. Con pago manual mes a mes
 * (sin cobro automático, ver FlowPaymentService) este aviso es lo único que le recuerda pagar
 * de nuevo antes de quedarse sin poder reservar.
 *
 * Pedido explícito del usuario: avisar "desde que le queden 2 días" — se manda una vez por día
 * mientras falten 2 o 1 días (dos avisos como máximo, uno por día, nunca antes de esa ventana).
 * El aviso de "ya venció" se dispara aparte, una sola vez, el día que se cumple.
 */
@Component
@RequiredArgsConstructor
public class MembershipReminderJob {

    private static final Logger log = LoggerFactory.getLogger(MembershipReminderJob.class);
    private static final ZoneId GYM_ZONE = ZoneId.of("America/Santiago");
    private static final long REMINDER_DAYS_BEFORE = 2;

    private final AppUserRepository appUserRepository;
    private final MemberService memberService;
    private final GymRepository gymRepository;
    private final GymPlanRepository gymPlanRepository;
    private final MemberLifecycleEmailService memberLifecycleEmailService;

    // Todos los días a las 09:00, hora de Chile — ni tan temprano que nadie lo vea, ni tan
    // tarde que un socio con la clase de la mañana ya se haya quedado sin poder reservar.
    @Scheduled(cron = "0 0 9 * * *", zone = "America/Santiago")
    public void sendReminders() {
        List<AppUser> members = appUserRepository.findByRoleAndPaidAtIsNotNull(Role.MEMBER);
        ZonedDateTime now = ZonedDateTime.now(GYM_ZONE);
        int sent = 0;
        for (AppUser member : members) {
            try {
                if (processMember(member, now)) {
                    sent++;
                }
            } catch (Exception e) {
                // Un socio con datos raros (plan borrado, gym borrado) nunca debe frenar el
                // aviso del resto — mismo criterio best-effort que el resto de los emails.
                log.error("No se pudo procesar el aviso de vencimiento para el socio {}: {}", member.getId(), e.getMessage());
            }
        }
        log.info("MembershipReminderJob: {} avisos enviados de {} socios con plan pagado alguna vez", sent, members.size());
    }

    private boolean processMember(AppUser member, ZonedDateTime now) {
        if (member.getPlanId() == null) {
            return false;
        }
        ZonedDateTime periodEnd = memberService.periodEnd(member);
        GymPlan plan = gymPlanRepository.findById(member.getPlanId()).orElse(null);
        Gym gym = gymRepository.findById(member.getGymId()).orElse(null);
        if (plan == null || gym == null) {
            return false;
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
            return true;
        }
        if (daysUntilExpiry == 0) {
            List<String> adminEmails = appUserRepository.findByGymIdAndRole(gym.getId(), Role.GYM_ADMIN).stream()
                    .map(AppUser::getEmail)
                    .toList();
            memberLifecycleEmailService.sendMembershipExpiredMember(gym, member, plan);
            memberLifecycleEmailService.sendMembershipExpiredAdmin(gym, member, plan, adminEmails);
            return true;
        }
        return false;
    }

    private static LocalDate today(ZonedDateTime value) {
        return value.withZoneSameInstant(GYM_ZONE).toLocalDate();
    }
}
