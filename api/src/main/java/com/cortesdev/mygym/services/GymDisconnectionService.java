package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.GymDeletionAudit;
import com.cortesdev.mygym.models.GymPhoto;
import com.cortesdev.mygym.models.GymPlan;
import com.cortesdev.mygym.models.Payment;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.GymDeletionAuditResponse;
import com.cortesdev.mygym.models.dto.GymDisconnectRequest;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymDeletionAuditRepository;
import com.cortesdev.mygym.repositories.GymPhotoRepository;
import com.cortesdev.mygym.repositories.GymPlanRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.PaymentRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.services.exception.GymDisconnectionEmailFailedException;
import com.cortesdev.mygym.services.exception.GymNameMismatchException;
import com.cortesdev.mygym.services.exception.GymNotFoundException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Desvinculación permanente de un gimnasio — pedido explícito del usuario: cuando un dueño de
 * gym cierra contrato con mygym para siempre, TODOS sus datos se borran, pero antes:
 * (1) se guarda un resumen SIN datos personales en gym_deletion_audit (para siempre, prueba de
 *     que se borró de verdad),
 * (2) se le manda a cada admin del gym el resumen + el detalle completo de sus socios (nombre,
 *     email, plan, pagos) en CSV — es SU única copia, mygym no la conserva,
 * (3) se le manda a mygym (contacto@mygym.cl) SOLO el resumen, sin datos personales — así mygym
 *     nunca se queda con una copia de los socios de un gimnasio que ya no es cliente.
 * Recién ahí se borra todo. Ver la conversación con el usuario del 2026-09-23 — cada una de
 * estas decisiones fue explícita, no asumida.
 *
 * El borrado en sí es liviano: `gymRepository.delete(gym)` alcanza. app_user, gym_block,
 * gym_plan, gym_photo y page_view tienen ON DELETE CASCADE directo desde gym.id (ver V1/V2/V7/
 * V8/V20); reservation y payment cascadean desde app_user.id (ver V2/V18). Todo en una sola
 * transacción de Postgres, sin necesidad de borrar tabla por tabla a mano.
 */
@Service
@RequiredArgsConstructor
public class GymDisconnectionService {

    private static final String MYGYM_CONTACT_EMAIL = "contacto@mygym.cl";
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.of("America/Santiago"));

    private final GymRepository gymRepository;
    private final AppUserRepository appUserRepository;
    private final GymBlockRepository gymBlockRepository;
    private final GymPlanRepository gymPlanRepository;
    private final GymPhotoRepository gymPhotoRepository;
    private final ReservationRepository reservationRepository;
    private final PaymentRepository paymentRepository;
    private final GymDeletionAuditRepository gymDeletionAuditRepository;
    private final GymDisconnectionEmailService emailService;
    // Solo para reusar periodEnd(user) — la misma cuenta de "1 mes calendario desde paidAt" que
    // ya usan ReservationService.book() y MembershipReminderJob, para no duplicarla acá (ver el
    // comentario en MemberService sobre por qué nunca se repite ese cálculo).
    private final MemberService memberService;

    @Transactional
    public GymDeletionAuditResponse disconnect(Long gymId, GymDisconnectRequest request, String executedByEmail) {
        Gym gym = gymRepository.findById(gymId).orElseThrow(() -> new GymNotFoundException(gymId));
        if (!gym.getName().equals(request.confirmGymName())) {
            throw new GymNameMismatchException();
        }

        List<AppUser> allUsers = appUserRepository.findByGymId(gymId);
        List<AppUser> admins = allUsers.stream().filter(u -> u.getRole() == Role.GYM_ADMIN).toList();
        List<AppUser> members = allUsers.stream().filter(u -> u.getRole() == Role.MEMBER).toList();
        List<Long> memberIds = members.stream().map(AppUser::getId).toList();

        List<GymPlan> plans = gymPlanRepository.findByGymId(gymId);
        Map<Long, String> planNames = plans.stream().collect(Collectors.toMap(GymPlan::getId, GymPlan::getName));
        List<GymBlock> blocks = gymBlockRepository.findByGymId(gymId);
        List<GymPhoto> photos = gymPhotoRepository.findByGymIdOrderBySortOrderAsc(gymId);
        List<Payment> payments = memberIds.isEmpty() ? List.of() : paymentRepository.findByMemberIdIn(memberIds);
        List<Reservation> reservations = memberIds.isEmpty() ? List.of() : reservationRepository.findByMemberIdIn(memberIds);

        Map<Long, Long> reservationCountByMember = reservations.stream()
                .collect(Collectors.groupingBy(Reservation::getMemberId, Collectors.counting()));
        Map<Long, String> memberEmailById =
                members.stream().collect(Collectors.toMap(AppUser::getId, AppUser::getEmail));
        Map<Long, Integer> lastPaidAmountByMember = lastPaidAmountByMember(payments);

        String membersCsv = buildMembersCsv(members, planNames, reservationCountByMember, lastPaidAmountByMember);
        String paymentsCsv = buildPaymentsCsv(payments, memberEmailById, planNames);

        // executedAt se fija a mano acá (no solo vía @PrePersist de GymDeletionAudit) porque
        // este objeto todavía sin guardar se usa para armar el email a los admins ANTES del
        // save() — sin esto, GymDisconnectionEmailService.buildHtml() intenta formatear un
        // Instant null y explota con NPE.
        GymDeletionAudit provisionalAudit = GymDeletionAudit.builder()
                .gymName(gym.getName())
                .gymSlug(gym.getSlug())
                .adminEmails(admins.stream().map(AppUser::getEmail).collect(Collectors.joining(", ")))
                .memberCount(members.size())
                .reservationCount(reservations.size())
                .paymentCount(payments.size())
                .blockCount(blocks.size())
                .planCount(plans.size())
                .photoCount(photos.size())
                .executedBy(executedByEmail)
                .executedAt(Instant.now())
                .build();

        // Los emails a los admins van ANTES de guardar auditoría o borrar nada: no podemos
        // borrar unilateralmente la cartera de clientes de un gym sin garantizar que su dueño
        // se quedó con una copia (socios.csv/pagos.csv) — si Resend falla para cualquier admin,
        // se aborta todo (ninguna fila queda escrita, @Transactional hace rollback) y el
        // super-admin ni se entera, porque en los hechos la desvinculación no ocurrió.
        for (AppUser admin : admins) {
            boolean sent = emailService.sendToGymAdmin(admin.getEmail(), gym, provisionalAudit, membersCsv, paymentsCsv);
            if (!sent) {
                throw new GymDisconnectionEmailFailedException(admin.getEmail());
            }
        }

        GymDeletionAudit audit = gymDeletionAuditRepository.save(provisionalAudit);

        // Best-effort: es solo la notificación interna de mygym, no implica pérdida de datos
        // para nadie — no aborta la desvinculación si Resend falla acá.
        emailService.sendSummaryToSuperAdmin(MYGYM_CONTACT_EMAIL, gym, audit);

        gymRepository.delete(gym);

        return toResponse(audit);
    }

    @Transactional(readOnly = true)
    public List<GymDeletionAuditResponse> listAudits() {
        return gymDeletionAuditRepository.findAllByOrderByExecutedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    private GymDeletionAuditResponse toResponse(GymDeletionAudit a) {
        return new GymDeletionAuditResponse(
                a.getId(),
                a.getGymName(),
                a.getGymSlug(),
                a.getAdminEmails(),
                a.getMemberCount(),
                a.getReservationCount(),
                a.getPaymentCount(),
                a.getBlockCount(),
                a.getPlanCount(),
                a.getPhotoCount(),
                a.getExecutedBy(),
                a.getExecutedAt());
    }

    private String buildMembersCsv(
            List<AppUser> members,
            Map<Long, String> planNames,
            Map<Long, Long> reservationCountByMember,
            Map<Long, Integer> lastPaidAmountByMember) {
        StringBuilder csv = new StringBuilder(
                "Nombre,Email,Plan,Monto pagado (CLP),Fecha de pago,Fecha de vencimiento del plan,Fecha de invitación,Fecha de alta,Reservas totales\n");
        for (AppUser m : members) {
            // El precio del plan puede haber cambiado desde que pagó — el monto real cobrado
            // sale del último Payment PAID de este socio, nunca del precio actual de GymPlan.
            Integer amountPaid = lastPaidAmountByMember.get(m.getId());
            // Mismo cálculo que MemberService.periodEnd (paidAt + 1 mes calendario) — el único
            // dato que ya usa el resto del producto para decidir si un socio sigue vigente.
            String periodEnd = m.getPaidAt() != null ? formatZonedDateTime(memberService.periodEnd(m)) : "";
            csv.append(csvField(m.getName()))
                    .append(',')
                    .append(csvField(m.getEmail()))
                    .append(',')
                    .append(csvField(m.getPlanId() != null ? planNames.getOrDefault(m.getPlanId(), "") : ""))
                    .append(',')
                    .append(amountPaid != null ? amountPaid : "")
                    .append(',')
                    .append(csvField(formatInstant(m.getPaidAt())))
                    .append(',')
                    .append(csvField(periodEnd))
                    .append(',')
                    .append(csvField(formatInstant(m.getInvitedAt())))
                    .append(',')
                    .append(csvField(formatInstant(m.getCreatedAt())))
                    .append(',')
                    .append(reservationCountByMember.getOrDefault(m.getId(), 0L))
                    .append('\n');
        }
        return csv.toString();
    }

    /** Último pago PAID de cada socio (por fecha de pago) — el monto real cobrado, no el precio
     *  vigente hoy del plan, que puede haber cambiado desde entonces. */
    private Map<Long, Integer> lastPaidAmountByMember(List<Payment> payments) {
        return payments.stream()
                .filter(p -> "PAID".equals(p.getStatus()) && p.getAmountClp() != null)
                .collect(Collectors.groupingBy(
                        Payment::getMemberId,
                        Collectors.collectingAndThen(
                                Collectors.maxBy(
                                        Comparator.comparing(p -> p.getPaidAt() != null ? p.getPaidAt() : Instant.EPOCH)),
                                opt -> opt.map(Payment::getAmountClp).orElse(null))));
    }

    private String formatZonedDateTime(ZonedDateTime value) {
        return value == null ? "" : DATE_FORMAT.format(value.toInstant());
    }

    private String buildPaymentsCsv(List<Payment> payments, Map<Long, String> memberEmailById, Map<Long, String> planNames) {
        StringBuilder csv = new StringBuilder("Email del socio,Plan,Monto CLP,Estado,Fecha de pago,Orden\n");
        for (Payment p : payments) {
            csv.append(csvField(memberEmailById.getOrDefault(p.getMemberId(), "")))
                    .append(',')
                    .append(csvField(p.getPlanId() != null ? planNames.getOrDefault(p.getPlanId(), "") : ""))
                    .append(',')
                    .append(p.getAmountClp() != null ? p.getAmountClp() : "")
                    .append(',')
                    .append(csvField(p.getStatus()))
                    .append(',')
                    .append(csvField(formatInstant(p.getPaidAt())))
                    .append(',')
                    .append(csvField(p.getCommerceOrder()))
                    .append('\n');
        }
        return csv.toString();
    }

    private String formatInstant(Instant instant) {
        return instant == null ? "" : DATE_FORMAT.format(instant);
    }

    /** Envuelve en comillas y escapa comillas internas si el valor trae coma, comilla o salto de
     *  línea — sin esto un nombre o plan con coma rompe el CSV. */
    private String csvField(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuoting = value.contains(",") || value.contains("\"") || value.contains("\n");
        String escaped = value.replace("\"", "\"\"");
        return needsQuoting ? "\"" + escaped + "\"" : escaped;
    }
}
