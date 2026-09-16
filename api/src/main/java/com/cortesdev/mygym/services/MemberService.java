package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.GymPlan;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.MemberCreateRequest;
import com.cortesdev.mygym.models.dto.MemberResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymPlanRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.services.exception.DuplicateMemberEmailException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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

    public MemberResponse createMember(Long gymId, MemberCreateRequest request) {
        if (appUserRepository.existsByEmail(request.email())) {
            throw new DuplicateMemberEmailException(request.email());
        }
        AppUser member = AppUser.builder()
                .name(request.name())
                .email(request.email())
                .role(Role.MEMBER)
                .gymId(gymId)
                .active(true)
                .build();
        return toResponse(appUserRepository.save(member));
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(Long gymId) {
        return appUserRepository.findByGymIdAndRole(gymId, Role.MEMBER).stream()
                .map(this::toResponse)
                .toList();
    }

    // Mismo umbral que expirySoon en member.ts (0 < díasRestantes <= 3).
    private static final int EXPIRING_SOON_DAYS = 3;

    private String membershipStatus(AppUser user) {
        Instant paidAt = user.getPaidAt();
        if (paidAt == null) {
            return "UNPAID";
        }
        ZonedDateTime now = ZonedDateTime.now(GYM_ZONE);
        ZonedDateTime periodEnd = paidAt.atZone(GYM_ZONE).plusMonths(1);
        if (!now.isBefore(periodEnd)) {
            return "EXPIRED";
        }
        long daysRemaining = Duration.between(now, periodEnd).toDays();
        return daysRemaining <= EXPIRING_SOON_DAYS ? "EXPIRING_SOON" : "ACTIVE";
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
                user.getPhotoUrl());
    }
}
