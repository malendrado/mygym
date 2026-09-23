package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymPlan;
import com.cortesdev.mygym.models.Payment;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.MemberImportRow;
import com.cortesdev.mygym.models.dto.MemberImportRowResult;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.PaymentRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Procesa UNA fila del Excel de importación masiva (ver MemberService.importMembers) en su
 * PROPIA transacción (REQUIRES_NEW) — separado de MemberService a propósito: un método privado
 * llamado por this.importRow(...) nunca pasa por el proxy de Spring, así que @Transactional no
 * tendría ningún efecto ahí. Sin una transacción propia por fila, un error real de Postgres a
 * mitad de un bloque de 20 (ej. una violación de constraint por una carrera real entre dos
 * subidas simultáneas) deja la transacción completa "abortada" del lado de Postgres — todas las
 * filas siguientes del mismo bloque fallarían también, aunque el código nunca relance esa
 * excepción. Con REQUIRES_NEW, un problema real en una fila queda contenido a esa fila sola.
 */
@Service
@RequiredArgsConstructor
public class MemberImportRowService {

    private static final ZoneId GYM_ZONE = ZoneId.of("America/Santiago");

    private final AppUserRepository appUserRepository;
    private final PaymentRepository paymentRepository;
    private final MemberLifecycleEmailService memberLifecycleEmailService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MemberImportRowResult importRow(
            Long gymId, Gym gym, MemberImportRow row, List<GymPlan> activePlansForInvite, Map<Long, GymPlan> plansById) {
        if (appUserRepository.existsByEmail(AppUser.normalizeEmail(row.email()))) {
            return new MemberImportRowResult(row.email(), false, "Ya existe un socio con ese email.");
        }

        // Plan/monto/vencimiento van los tres juntos o los tres vacíos — un plan sin pago dejaría
        // a este socio en un estado (planId seteado, paidAt null) que no existe en ningún otro
        // flujo de la app y que membershipStatus() no interpreta como "tiene plan" ni "no tiene".
        boolean hasPlanInfo = row.planId() != null || row.amountClp() != null || row.expiresAt() != null;
        GymPlan plan = null;
        Instant paidAt = null;
        if (hasPlanInfo) {
            if (row.planId() == null || row.amountClp() == null || row.amountClp() <= 0 || row.expiresAt() == null) {
                return new MemberImportRowResult(
                        row.email(),
                        false,
                        "Para asignar un plan hacen falta Plan, Monto pagado y Fecha de vencimiento completos.");
            }
            // plansById solo trae planes ACTIVOS (ver MemberService.importMembers) — un plan
            // desactivado justo entre que el admin abrió el modal y confirmó el import cae acá
            // igual que uno inexistente, nunca se acepta a medias.
            plan = plansById.get(row.planId());
            if (plan == null) {
                return new MemberImportRowResult(row.email(), false, "El plan no existe o no pertenece a este gimnasio.");
            }
            // Misma regla que MemberService.periodEnd (paidAt + 1 mes calendario) pero a la
            // inversa: el Excel trae la fecha de vencimiento (lo que el admin realmente sabe de
            // memoria), acá se deriva el paidAt que el resto del sistema espera.
            paidAt = row.expiresAt().atZone(GYM_ZONE).minusMonths(1).toInstant();

            if (row.usedSessions() != null) {
                if (plan.getMonthlyClasses() == null) {
                    // Plan libre/ilimitado — "clases usadas" no tiene contra qué medirse, se ignora
                    // en vez de rechazar la fila entera (el frontend ya avisa esto como advertencia,
                    // no como error — ver ImportMembersModal).
                    row = new MemberImportRow(row.name(), row.email(), row.planId(), row.amountClp(), row.expiresAt(), null);
                } else if (row.usedSessions() < 0 || row.usedSessions() > plan.getMonthlyClasses()) {
                    return new MemberImportRowResult(
                            row.email(),
                            false,
                            "Clases usadas debe estar entre 0 y el cupo del plan (" + plan.getMonthlyClasses() + ").");
                }
            }
        }

        AppUser.AppUserBuilder builder = AppUser.builder()
                .name(row.name())
                .email(row.email())
                .role(Role.MEMBER)
                .gymId(gymId)
                .active(true)
                .invitedAt(Instant.now());
        if (plan != null) {
            builder.planId(plan.getId()).paidAt(paidAt).usedSessionsAtImport(row.usedSessions());
        }
        AppUser member = appUserRepository.save(builder.build());

        if (plan != null) {
            // Fila real en Payment (no solo planId/paidAt en AppUser) para que este socio importado
            // quede indistinguible de uno que pagó por Flow ante cualquier reporte que lea Payment
            // como fuente de verdad del monto — ver GymDisconnectionService.lastPaidAmountByMember.
            paymentRepository.save(Payment.builder()
                    .memberId(member.getId())
                    .planId(plan.getId())
                    .amountClp(row.amountClp())
                    .status("PAID")
                    .paidAt(paidAt)
                    .build());
            // sendMemberImportedWithPlan, no sendPaymentConfirmedMember — ese último no saluda por
            // nombre ni dice fecha de vencimiento (asume que el socio recién pagó él mismo, así que
            // "activo" siempre es cierto); acá el socio nunca se logueó y la fecha viene del Excel,
            // puede estar vencida. Se pasa row.expiresAt() (la fecha tal cual la cargó el admin), no
            // periodEnd recalculado desde paidAt, para no arrastrar ningún redondeo/drift.
            memberLifecycleEmailService.sendMemberImportedWithPlan(
                    gym, member, plan, row.expiresAt().atZone(GYM_ZONE), row.usedSessions());
        } else {
            memberLifecycleEmailService.sendMemberInviteWithPlans(gym, member, activePlansForInvite);
        }

        return new MemberImportRowResult(row.email(), true, null);
    }
}
