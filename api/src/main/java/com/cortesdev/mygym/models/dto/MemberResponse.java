package com.cortesdev.mygym.models.dto;

import com.cortesdev.mygym.models.Role;
import java.time.Instant;
import java.time.LocalDate;

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
        String photoUrl,
        /** null (se auto-registró) | "PENDING" (el admin lo agregó a mano, todavía no entró con Google) |
         *  "REGISTERED" (el admin lo agregó a mano y ya entró al menos una vez) — eje independiente de
         *  membershipStatus, ver MemberService.inviteStatus. */
        String inviteStatus,
        /** "Lun 19:00 GAP · Mié 19:00 GAP" — null si no tiene clases fijas. */
        String fixedClassesSummary,
        /** Datos de contacto del socio — null mientras no los complete (ver AppUser.isProfileComplete). */
        String rut,
        String address,
        LocalDate birthDate,
        /** Formato internacional ("+56912345678") — el panel arma el link de WhatsApp con esto. */
        String phone,
        boolean profileComplete) {

    /** Sin los datos sensibles (RUT, dirección, nacimiento); hoy no se usa: el PROFESOR también los ve y edita. */
    public MemberResponse withoutSensitiveData() {
        return new MemberResponse(id, name, email, role, gymId, active, createdAt, planId, paidAt, membershipStatus,
                planName, planEndDate, monthlyClasses, sessionsRemaining, photoUrl, inviteStatus, fixedClassesSummary,
                null, null, null, phone, profileComplete);
    }
}
