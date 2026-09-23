package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymPlan;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.MemberCreateRequest;
import com.cortesdev.mygym.models.dto.MemberImportRow;
import com.cortesdev.mygym.models.dto.MemberImportRowResult;
import com.cortesdev.mygym.models.dto.MemberResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymPlanRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.services.exception.DuplicateMemberEmailException;
import com.cortesdev.mygym.services.exception.GymNotFoundException;
import com.cortesdev.mygym.services.exception.MemberNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(MemberService.class);

    // Tope defensivo por request — el frontend ya manda el Excel en bloques de ~20 filas (ver
    // ImportMembersModal), esto es solo una red de seguridad contra un bug del cliente.
    private static final int MAX_IMPORT_ROWS = 100;

    private final AppUserRepository appUserRepository;
    private final GymPlanRepository gymPlanRepository;
    private final ReservationRepository reservationRepository;
    private final GymRepository gymRepository;
    private final MemberLifecycleEmailService memberLifecycleEmailService;
    private final MemberImportRowService memberImportRowService;

    public MemberResponse createMember(Long gymId, MemberCreateRequest request) {
        if (appUserRepository.existsByEmail(AppUser.normalizeEmail(request.email()))) {
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

    // Carga masiva desde el Excel del admin (ver ImportMembersModal en el frontend) — cada fila
    // se procesa de forma INDEPENDIENTE (try/catch propio), nunca aborta el resto del lote si una
    // fila falla. El frontend manda esto en bloques de ~20 filas, no el archivo entero de una vez.
    public List<MemberImportRowResult> importMembers(Long gymId, List<MemberImportRow> rows) {
        if (rows.size() > MAX_IMPORT_ROWS) {
            throw new IllegalArgumentException("No se pueden importar más de " + MAX_IMPORT_ROWS + " filas por vez.");
        }
        Gym gym = gymRepository.findById(gymId).orElseThrow(() -> new GymNotFoundException(gymId));
        List<GymPlan> activeGymPlans =
                gymPlanRepository.findByGymId(gymId).stream().filter(GymPlan::isActive).toList();
        List<GymPlan> activePlansForInvite = activeGymPlans.stream()
                .sorted(Comparator.comparing(GymPlan::getPriceClp, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        // Solo planes ACTIVOS — un planId de un plan desactivado se rechaza igual que uno
        // inexistente (ver MemberImportRowService.importRow), nunca se acepta a medias.
        Map<Long, GymPlan> plansById = activeGymPlans.stream().collect(Collectors.toMap(GymPlan::getId, p -> p));

        List<MemberImportRowResult> results = new ArrayList<>();
        for (MemberImportRow row : rows) {
            try {
                // REQUIRES_NEW en MemberImportRowService — cada fila corre en su propia
                // transacción, así un error real de Postgres a mitad de un bloque nunca deja
                // "abortada" la transacción para las filas siguientes del mismo bloque.
                results.add(memberImportRowService.importRow(gymId, gym, row, activePlansForInvite, plansById));
            } catch (Exception e) {
                log.error("Fila de importación falló para {}: {}", row.email(), e.getMessage(), e);
                results.add(new MemberImportRowResult(row.email(), false, "Error inesperado al importar esta fila."));
            }
        }
        return results;
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(Long gymId) {
        return appUserRepository.findByGymIdAndRole(gymId, Role.MEMBER).stream()
                .map(this::toResponse)
                .toList();
    }

    // Borrado permanente — a diferencia de revokePlan (solo le saca el plan), esto elimina
    // la fila del socio por completo. reservation y payment tienen ON DELETE CASCADE sobre
    // member_id (reservation desde siempre, payment desde V18), así que el borrado de la fila
    // ya se lleva puesto su historial de reservas y de pagos sin borrarlos a mano acá. Solo
    // super-admin puede hacerlo (GymController), nunca el propio gym-admin — y solo aplica a
    // un MEMBER real, nunca a un GYM_ADMIN/SUPER_ADMIN (evita borrar por error al dueño del gym).
    public void deleteMember(Long gymId, Long memberId) {
        AppUser member = appUserRepository
                .findByIdAndGymId(memberId, gymId)
                .filter(u -> u.getRole() == Role.MEMBER)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
        appUserRepository.delete(member);
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
                    int usedAtImport = user.getUsedSessionsAtImport() != null ? user.getUsedSessionsAtImport() : 0;
                    sessionsRemaining = Math.max(0, monthlyClasses - used - usedAtImport);
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
