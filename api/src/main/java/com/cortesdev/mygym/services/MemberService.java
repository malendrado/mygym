package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.GymPlan;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.MemberCreateRequest;
import com.cortesdev.mygym.models.dto.MemberResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymPlanRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.services.exception.DuplicateMemberEmailException;
import com.cortesdev.mygym.services.exception.MemberNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class MemberService {

    // Mismo huso y misma regla de "1 mes calendario desde el pago" que
    // usaba member.ts client-side (membershipPeriodEnd) — ahora calculada acá
    // porque paidAt ya se persiste de verdad (ver GymService.simulatePlanPayment).
    private static final ZoneId GYM_ZONE = ZoneId.of("America/Santiago");

    private final AppUserRepository appUserRepository;
    private final GymPlanRepository gymPlanRepository;
    private final ReservationRepository reservationRepository;
    private final GymRepository gymRepository;
    private final MemberLifecycleEmailService memberLifecycleEmailService;

    public MemberResponse createMember(Long gymId, MemberCreateRequest request) {
        if (appUserRepository.existsByEmail(request.email())) {
            throw new DuplicateMemberEmailException(request.email());
        }
        AppUser member = appUserRepository.save(AppUser.builder()
                .name(request.name())
                .email(request.email())
                .role(Role.MEMBER)
                .gymId(gymId)
                .active(true)
                .invitedAt(Instant.now())
                .build());

        // Alta manual por el admin — a diferencia de /j/{slug} (alta pública
        // con Google), acá el socio no recibía ningún email hasta ahora y
        // quedaba pasivo en la base sin ninguna forma de saber que ya podía
        // entrar. Reusa MemberLifecycleEmailService (best-effort, nunca
        // rompe este flujo si Resend falla).
        gymRepository.findById(gymId).ifPresent(gym -> {
            List<GymPlan> activePlans = gymPlanRepository.findByGymId(gymId).stream()
                    .filter(GymPlan::isActive)
                    .sorted(Comparator.comparing(GymPlan::getPriceClp, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
            memberLifecycleEmailService.sendMemberInviteWithPlans(gym, member, activePlans);
        });

        return toResponse(member);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(Long gymId) {
        return appUserRepository.findByGymIdAndRole(gymId, Role.MEMBER).stream()
                .map(this::toResponse)
                .toList();
    }

    // Antes /member (autoservicio del socio) nunca leía de vuelta su propio
    // plan/paidAt reales — el signal `membership` del frontend arrancaba
    // siempre en "none" sin importar lo que un admin ya hubiera marcado
    // como pagado. Reportado por el usuario: un socio con plan "Activo"
    // visto desde el panel del admin veía "Elige tu plan" en su propia
    // vista.
    @Transactional(readOnly = true)
    public MemberResponse getOwnMembership(Long userId) {
        AppUser user = appUserRepository.findById(userId).orElseThrow(() -> new MemberNotFoundException(userId));
        return toResponse(user);
    }

    // Mismo umbral que expirySoon en member.ts (0 < díasRestantes <= 3).
    private static final int EXPIRING_SOON_DAYS = 3;

    // Eje independiente de membershipStatus (pago) — mide si este socio vino
    // de una alta manual del admin y, si vino de ahí, si ya llegó a activar
    // su cuenta con Google. invitedAt se setea una sola vez, en createMember;
    // el alta pública (/j/{slug}) nunca lo toca, así que un socio auto-
    // registrado siempre da null acá.
    private String inviteStatus(AppUser user) {
        if (user.getInvitedAt() == null) {
            return null;
        }
        return user.getGoogleSub() != null ? "REGISTERED" : "PENDING";
    }

    // Usado como gate real de autorización por ReservationService.book() — hasta que esto
    // existió, cualquier socio podía reservar clases gratis sin importar si había pagado o
    // no (membershipStatus era puramente informativo para la UI). Reusa el mismo cálculo,
    // nunca duplica la lógica de "1 mes calendario desde paidAt".
    public boolean hasActiveMembership(AppUser user) {
        String status = membershipStatus(user);
        return "ACTIVE".equals(status) || "EXPIRING_SOON".equals(status);
    }

    private String membershipStatus(AppUser user) {
        Instant paidAt = user.getPaidAt();
        if (paidAt == null) {
            return "UNPAID";
        }
        ZonedDateTime now = ZonedDateTime.now(GYM_ZONE);
        ZonedDateTime periodEnd = periodEnd(user);
        if (!now.isBefore(periodEnd)) {
            return "EXPIRED";
        }
        long daysRemaining = Duration.between(now, periodEnd).toDays();
        return daysRemaining <= EXPIRING_SOON_DAYS ? "EXPIRING_SOON" : "ACTIVE";
    }

    // Usado también por MembershipReminderJob — nunca duplicar la cuenta de "1 mes calendario
    // desde paidAt" en dos lugares (así fue como se armó membershipStatus originalmente).
    public ZonedDateTime periodEnd(AppUser user) {
        return user.getPaidAt().atZone(GYM_ZONE).plusMonths(1);
    }

    private MemberResponse toResponse(AppUser user) {
        String planName = null;
        Instant planEndDate = null;
        Integer monthlyClasses = null;
        Integer sessionsRemaining = null;
        Long planId = user.getPlanId();
        Instant paidAt = user.getPaidAt();
        if (planId != null && paidAt != null) {
            ZonedDateTime periodStart = paidAt.atZone(GYM_ZONE);
            ZonedDateTime periodEnd = periodStart.plusMonths(1);
            planEndDate = periodEnd.toInstant();
            GymPlan plan = gymPlanRepository.findById(planId).orElse(null);
            if (plan != null) {
                planName = plan.getName();
                monthlyClasses = plan.getMonthlyClasses();
                if (monthlyClasses != null) {
                    int used = reservationRepository.countByMemberIdAndStatusAndClassDateBetween(
                            user.getId(), ReservationStatus.BOOKED, periodStart.toLocalDate(), periodEnd.toLocalDate());
                    sessionsRemaining = Math.max(0, monthlyClasses - used);
                }
            }
        }
        return new MemberResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getGymId(),
                user.isActive(),
                user.getCreatedAt(),
                user.getPlanId(),
                user.getPaidAt(),
                membershipStatus(user),
                planName,
                planEndDate,
                monthlyClasses,
                sessionsRemaining,
                user.getPhotoUrl(),
                inviteStatus(user));
    }
}
