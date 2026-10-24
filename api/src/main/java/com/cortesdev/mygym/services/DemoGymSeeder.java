package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.CheckinMethod;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.GymPlan;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymPlanRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mantiene "vivo" el gimnasio de demostración comercial (slug {@code demo}): sin esto la demo se veía vacía (agenda con "0/15 cupos",
 * asistencia sin datos, sin profesores ni administradores, socios llamados "Demo Activo 7"). Cada vez que corre deja, de forma
 * idempotente (correrlo dos veces no cambia nada):
 * <ul>
 *   <li>nombres de persona en los socios que todavía se llaman "Demo ...";</li>
 *   <li>dos profesores y un administrador ficticios (los de la agenda: Camila Rojas y Martín Ibáñez);</li>
 *   <li>reservas en las clases de las últimas 3 semanas y de la próxima, con asistencia marcada en las ya terminadas.</li>
 * </ul>
 * Las reservas son relativas a HOY, por eso corre al arrancar y cada 6 horas: la demo nunca "envejece". Escribe por repositorio, no por
 * los servicios de negocio, a propósito: así no dispara correos ni avisos push. Solo usa socios con plan libre (no consume clases de
 * planes con cupo) y no toca ninguna reserva existente. Si el gimnasio {@code demo} no existe (local, tests, otro ambiente) no hace nada.
 * Respeta {@code SCHEDULING_ENABLED=false} como los demás crons (una copia de respaldo no debe escribir en la base real).
 */
@Component
@RequiredArgsConstructor
public class DemoGymSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoGymSeeder.class);
    static final String DEMO_SLUG = "demo";
    private static final ZoneId GYM_ZONE = ZoneId.of("America/Santiago");
    private static final int DAYS_BACK = 21;
    private static final int DAYS_AHEAD = 7;
    private static final Pattern PLACEHOLDER_NAME = Pattern.compile("^Demo\\b.*", Pattern.CASE_INSENSITIVE);
    /** example.com está reservado (RFC 2606): ningún correo real llega ahí, y nadie puede entrar con Google con esas cuentas. */
    private static final String STAFF_DOMAIN = "@example.com";

    static final List<String> NAMES = List.of(
            "Valentina Soto", "Matías Contreras", "Francisca Muñoz", "Sebastián Araya", "Javiera Pizarro", "Diego Fuentealba",
            "Constanza Rivas", "Nicolás Bravo", "Antonia Vergara", "Felipe Escobar", "Catalina Tapia", "Joaquín Sepúlveda",
            "Camila Orellana", "Cristóbal Vidal", "Fernanda Campos", "Tomás Figueroa", "Isidora Carrasco", "Benjamín Olivares",
            "Macarena Leiva", "Ignacio Espinoza", "Daniela Henríquez", "Vicente Aguilera", "Paula Cárdenas", "Gabriel Núñez",
            "Trinidad Salinas", "Maximiliano Rojas", "Josefa Morales", "Agustín Valdés", "Amanda Parra", "Lucas Medina",
            "Florencia Navarro", "Martina Godoy", "Renato Bustos", "Pilar Zúñiga", "Andrés Castillo", "Carolina Lagos",
            "Rodrigo Maldonado", "Sofía Gallardo", "Esteban Poblete", "Marcela Reyes", "Gonzalo Riquelme", "Bárbara Toro",
            "Patricio Ibarra", "Natalia Cortés", "Hernán Segura", "Ximena Jara", "Álvaro Peña", "Rocío Mella",
            "Claudio Sandoval", "Lorena Yáñez", "Raúl Cabrera", "Tamara Villalobos", "Ariel Urrutia", "Belén Cifuentes",
            "Emilio Garrido", "Karen Miranda", "Samuel Lizama", "Yasna Alarcón", "Bruno Ortiz", "Dominique Fernández");

    private record Staff(String name, String email, Role role) {}

    private static final List<Staff> STAFF = List.of(
            new Staff("Camila Rojas", "camila.rojas" + STAFF_DOMAIN, Role.PROFESOR),
            new Staff("Martín Ibáñez", "martin.ibanez" + STAFF_DOMAIN, Role.PROFESOR),
            new Staff("Valentina Soto", "valentina.soto" + STAFF_DOMAIN, Role.GYM_ADMIN));

    private final GymRepository gymRepository;
    private final AppUserRepository appUserRepository;
    private final GymBlockRepository gymBlockRepository;
    private final GymPlanRepository gymPlanRepository;
    private final ReservationRepository reservationRepository;

    @Scheduled(initialDelay = 120_000, fixedDelay = 6 * 60 * 60 * 1000L)
    @Transactional
    public void refresh() {
        gymRepository.findBySlug(DEMO_SLUG).ifPresent(this::refresh);
    }

    void refresh(Gym gym) {
        Long gymId = gym.getId();
        List<AppUser> members = appUserRepository.findByGymIdAndRole(gymId, Role.MEMBER);
        int renamed = giveRealNames(members);
        int staff = ensureStaff(gymId);
        int reservations = fillClasses(gymId, members);
        if (renamed + staff + reservations > 0) {
            log.info("Demo refrescada: {} socios renombrados, {} cuentas de equipo, {} reservas nuevas", renamed, staff, reservations);
        }
    }

    private int giveRealNames(List<AppUser> members) {
        Set<String> used = members.stream().map(AppUser::getName).filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        List<AppUser> changed = new ArrayList<>();
        for (AppUser member : members) {
            String name = member.getName();
            if (name == null || !PLACEHOLDER_NAME.matcher(name.trim()).matches()) {
                continue;
            }
            // Mismo socio, mismo nombre en cada corrida; si ya está tomado se avanza al siguiente de la lista.
            int start = (int) Math.floorMod(member.getId(), (long) NAMES.size());
            for (int i = 0; i < NAMES.size(); i++) {
                String candidate = NAMES.get((start + i) % NAMES.size());
                if (used.add(candidate)) {
                    member.setName(candidate);
                    changed.add(member);
                    break;
                }
            }
        }
        appUserRepository.saveAll(changed);
        return changed.size();
    }

    private int ensureStaff(Long gymId) {
        int created = 0;
        for (Staff s : STAFF) {
            if (appUserRepository.findByEmail(AppUser.normalizeEmail(s.email())).isPresent()) {
                continue;
            }
            appUserRepository.save(AppUser.builder()
                    .email(s.email())
                    .name(s.name())
                    .role(s.role())
                    .gymId(gymId)
                    .active(true)
                    .build());
            created++;
        }
        return created;
    }

    private int fillClasses(Long gymId, List<AppUser> members) {
        Set<Long> freePlanIds = gymPlanRepository.findByGymId(gymId).stream()
                .filter(GymPlan::isActive)
                .filter(p -> p.getMonthlyClasses() == null)
                .map(GymPlan::getId)
                .collect(Collectors.toSet());
        // Orden estable por id: la misma lista de socios da las mismas reservas en cada corrida.
        List<AppUser> pool = members.stream()
                .filter(AppUser::isActive)
                .filter(m -> m.getPlanId() != null && freePlanIds.contains(m.getPlanId()))
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .toList();
        List<GymBlock> blocks = gymBlockRepository.findByGymId(gymId).stream().filter(GymBlock::isActive).toList();
        if (pool.isEmpty() || blocks.isEmpty()) {
            return 0;
        }
        ZonedDateTime now = ZonedDateTime.now(GYM_ZONE);
        LocalDate today = now.toLocalDate();
        List<Occurrence> upcoming = upcomingOccurrences(blocks, now);
        int sample = bookSampleMemberUpcoming(members, pool, upcoming);
        List<Reservation> toSave = new ArrayList<>();
        for (GymBlock block : blocks) {
            for (LocalDate date = today.minusDays(DAYS_BACK); !date.isAfter(today.plusDays(DAYS_AHEAD)); date = date.plusDays(1)) {
                if (date.getDayOfWeek() != block.getDayOfWeek()) {
                    continue;
                }
                fillOccurrence(block, date, pool, now, toSave);
            }
        }
        reservationRepository.saveAll(toSave);
        return sample + toSave.size() + fillOneUpcomingClass(members, pool, upcoming);
    }

    private record Occurrence(GymBlock block, LocalDate date) {}

    /** Próximas clases (todavía no empiezan) dentro de la ventana, de la más cercana a la más lejana. */
    private List<Occurrence> upcomingOccurrences(List<GymBlock> blocks, ZonedDateTime now) {
        LocalDate today = now.toLocalDate();
        List<Occurrence> upcoming = new ArrayList<>();
        for (GymBlock block : blocks) {
            for (LocalDate date = today; !date.isAfter(today.plusDays(DAYS_AHEAD)); date = date.plusDays(1)) {
                if (date.getDayOfWeek() == block.getDayOfWeek() && date.atTime(block.getStartTime()).atZone(GYM_ZONE).isAfter(now)) {
                    upcoming.add(new Occurrence(block, date));
                }
            }
        }
        upcoming.sort(Comparator.comparing(Occurrence::date).thenComparing(o -> o.block().getStartTime()));
        return upcoming;
    }

    private List<Reservation> bookedIn(Occurrence occurrence) {
        return reservationRepository.findByGymBlockIdAndClassDateAndStatus(
                occurrence.block().getId(), occurrence.date(), ReservationStatus.BOOKED);
    }

    private void book(Occurrence occurrence, AppUser member) {
        reservationRepository.save(Reservation.builder()
                .gymBlockId(occurrence.block().getId())
                .memberId(member.getId())
                .classDate(occurrence.date())
                .status(ReservationStatus.BOOKED)
                .build());
    }

    /**
     * "Ver como socio" muestra al PRIMER socio del gimnasio (DemoPreviewController.sampleMemberId, mismo criterio acá): sin próximas
     * reservas su pestaña "Mis reservas" aparece vacía (y el paso "Cancela cuando quiera" del tour no tiene qué iluminar). Se le
     * aseguran las dos próximas clases, si tiene plan libre y quedan cupos.
     */
    private int bookSampleMemberUpcoming(List<AppUser> members, List<AppUser> pool, List<Occurrence> upcoming) {
        if (members.isEmpty() || !pool.contains(members.get(0))) {
            return 0;
        }
        AppUser sampleMember = members.get(0);
        int created = 0;
        for (Occurrence occurrence : upcoming.stream().limit(2).toList()) {
            List<Reservation> existing = bookedIn(occurrence);
            Integer capacity = occurrence.block().getCapacity();
            boolean already = existing.stream().anyMatch(r -> r.getMemberId().equals(sampleMember.getId()));
            if (already || capacity == null || existing.size() >= capacity) {
                continue;
            }
            book(occurrence, sampleMember);
            created++;
        }
        return created;
    }

    /**
     * Deja UNA próxima clase con el cupo lleno y sin el socio de muestra: es la que muestra "Anotarme en la lista de espera" (paso
     * "Si está llena, se anota" del tour de socio). Se elige, entre las que vienen después de las dos del socio de muestra, la de menor
     * cupo, para que alcancen los socios del plan libre.
     */
    private int fillOneUpcomingClass(List<AppUser> members, List<AppUser> pool, List<Occurrence> upcoming) {
        Long sampleId = members.isEmpty() ? null : members.get(0).getId();
        List<AppUser> others = pool.stream().filter(m -> !m.getId().equals(sampleId)).toList();
        Occurrence chosen = upcoming.stream()
                .skip(2)
                .filter(o -> o.block().getCapacity() != null && o.block().getCapacity() > 0 && o.block().getCapacity() <= others.size())
                .filter(o -> bookedIn(o).stream().noneMatch(r -> r.getMemberId().equals(sampleId)))
                .min(Comparator.comparing((Occurrence o) -> o.block().getCapacity()))
                .orElse(null);
        if (chosen == null) {
            return 0;
        }
        List<Reservation> existing = bookedIn(chosen);
        Set<Long> booked = existing.stream().map(Reservation::getMemberId).collect(Collectors.toCollection(HashSet::new));
        int created = 0;
        for (AppUser member : others) {
            if (existing.size() + created >= chosen.block().getCapacity()) {
                break;
            }
            if (booked.add(member.getId())) {
                book(chosen, member);
                created++;
            }
        }
        return created;
    }

    private void fillOccurrence(GymBlock block, LocalDate date, List<AppUser> pool, ZonedDateTime now, List<Reservation> toSave) {
        List<Reservation> existing = reservationRepository.findByGymBlockIdAndClassDateAndStatus(block.getId(), date, ReservationStatus.BOOKED);
        int capacity = block.getCapacity() == null ? 0 : block.getCapacity();
        // Entre 35 % y 85 % del cupo, fijo para cada (clase, día): se ve natural y no cambia entre corridas.
        int hash = Math.floorMod(Objects.hash(block.getId(), date.toEpochDay()), 1000);
        int target = Math.min(pool.size(), Math.min(capacity, Math.max(2, (int) Math.round(capacity * (0.35 + 0.5 * hash / 1000.0)))));
        if (existing.size() >= target) {
            return;
        }
        Set<Long> booked = existing.stream().map(Reservation::getMemberId).collect(Collectors.toCollection(HashSet::new));
        ZonedDateTime start = date.atTime(block.getStartTime()).atZone(GYM_ZONE);
        boolean finished = date.atTime(block.getEndTime()).atZone(GYM_ZONE).isBefore(now);
        int offset = Math.floorMod(Objects.hash(block.getId(), date.toEpochDay(), "who"), pool.size());
        int missing = target - existing.size();
        for (int i = 0; i < pool.size() && missing > 0; i++) {
            AppUser member = pool.get((offset + i) % pool.size());
            if (!booked.add(member.getId())) {
                continue;
            }
            Reservation.ReservationBuilder r = Reservation.builder()
                    .gymBlockId(block.getId())
                    .memberId(member.getId())
                    .classDate(date)
                    .status(ReservationStatus.BOOKED);
            if (finished) {
                // La clase ya pasó: ~80 % vino (marcado por QR o a mano), el resto queda como "no vino".
                // Los recordatorios push de clases pasadas se dan por resueltos para que ClassReminderJob no los evalúe.
                long salt = member.getId() + date.toEpochDay();
                if (salt % 5 != 0) {
                    r.checkedInAt(start.plusMinutes(2 + salt % 6).toInstant());
                    r.checkinMethod(salt % 2 == 0 ? CheckinMethod.QR : CheckinMethod.MANUAL);
                }
                r.reminderPushedAt(start.toInstant());
            }
            toSave.add(r.build());
            missing--;
        }
    }
}
