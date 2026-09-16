package com.cortesdev.mygym.models.dto;

import com.cortesdev.mygym.models.Role;
import java.time.Instant;

public record MemberResponse(
        Long id,
        String name,
        String email,
        Role role,
        Long gymId,
        boolean active,
        Instant createdAt,
        Long planId,
        Instant paidAt,
        /** "ACTIVE" | "EXPIRING_SOON" | "EXPIRED" | "UNPAID" — calculado en el servidor, ver MemberService.membershipStatus. */
        String membershipStatus,
        /** Nombre del plan contratado — null si nunca se marcó un pago (planId null). */
        String planName,
        /** paidAt + 1 mes calendario — null si nunca se marcó un pago. */
        Instant planEndDate,
        /** Cupo mensual del plan — null si el plan es libre (ilimitado) o no hay plan. */
        Integer monthlyClasses,
        /** monthlyClasses menos reservas BOOKED en el período actual — null si el plan es ilimitado o no hay plan. */
        Integer sessionsRemaining,
        /** Foto de perfil de Google — null si el socio nunca se logueó con Google. */
        String photoUrl) {}
