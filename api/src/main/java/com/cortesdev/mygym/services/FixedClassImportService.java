package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.MemberFixedClass;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.FixedClassImportMemberResult;
import com.cortesdev.mygym.models.dto.FixedClassImportRow;
import com.cortesdev.mygym.models.dto.FixedClassRowResult;
import com.cortesdev.mygym.models.dto.FixedClassesSaveResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.MemberFixedClassRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Carga masiva de clases fijas desde Excel (2026-10-12). Dos pasos, mismas reglas:
 * <ol>
 *   <li>{@link #validate}: revisa TODAS las filas y devuelve el resumen por fila SIN escribir nada
 *       (el panel lo muestra como vista previa; la lista de socios del panel está paginada, así
 *       que el email solo se puede comprobar acá).
 *   <li>{@link #importRows}: vuelve a validar (nunca confía en que se hizo la vista previa) y carga
 *       socio por socio. Este bean NO es transaccional a propósito: cada socio es su propia
 *       transacción (FixedClassService.replaceForMember), así uno con error no tumba a los demás.
 * </ol>
 * La clase se identifica por día + hora + NOMBRE: un gym puede tener GAP y CrossFit a la misma
 * hora, así que nunca se elige un bloque solo por día y hora.
 */
@Service
@RequiredArgsConstructor
public class FixedClassImportService {

    private static final Logger log = LoggerFactory.getLogger(FixedClassImportService.class);
    private static final ZoneId GYM_ZONE = ZoneId.of("America/Santiago");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    public static final String OK = "OK";
    public static final String WARNING = "WARNING";
    public static final String ERROR = "ERROR";
    /** Fila válida cuyo socio tiene OTRA fila con error: se omite entera (el archivo reemplaza el
     *  set completo del socio, cargarlo a medias le dejaría clases fijas incompletas). */
    public static final String OMITTED = "OMITTED";

    private final AppUserRepository appUserRepository;
    private final GymBlockRepository gymBlockRepository;
    private final MemberFixedClassRepository fixedClassRepository;
    private final ReservationRepository reservationRepository;
    private final MemberService memberService;
    private final FixedClassService fixedClassService;

    /** Valida sin escribir nada. El resultado tiene una entrada por fila, en el mismo orden. */
    public List<FixedClassRowResult> validate(Long gymId, List<FixedClassImportRow> rows) {
        return analyze(gymId, rows).results();
    }

    /** Carga confirmada: un resultado por socio (en orden de aparición en el archivo). */
    public List<FixedClassImportMemberResult> importRows(Long gymId, List<FixedClassImportRow> rows, Long actorUserId) {
        Analysis analysis = analyze(gymId, rows);
        Map<String, List<Integer>> rowsByEmail = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            rowsByEmail.computeIfAbsent(AppUser.normalizeEmail(rows.get(i).email()), k -> new ArrayList<>()).add(i);
        }

        List<FixedClassImportMemberResult> out = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> entry : rowsByEmail.entrySet()) {
            String email = entry.getKey();
            List<FixedClassRowResult> memberResults =
                    entry.getValue().stream().map(i -> analysis.results().get(i)).toList();
            FixedClassRowResult blocking = memberResults.stream()
                    .filter(r -> ERROR.equals(r.status()) || OMITTED.equals(r.status()))
                    .findFirst()
                    .orElse(null);
            AppUser member = analysis.membersByEmail().get(email);
            String name = member != null ? member.getName() : null;
            if (blocking != null || member == null) {
                out.add(new FixedClassImportMemberResult(
                        email, name, false, blocking != null ? blocking.message() : "Socio no encontrado.", 0, 0));
                continue;
            }
            List<Long> blockIds = entry.getValue().stream().map(analysis.blockIdByRow()::get).toList();
            try {
                FixedClassesSaveResponse saved = fixedClassService.replaceForMember(gymId, member.getId(), blockIds, actorUserId);
                out.add(new FixedClassImportMemberResult(email, name, true, describeSaved(saved), saved.created(), saved.skipped().size()));
            } catch (RuntimeException e) {
                log.warn("No se pudieron cargar las clases fijas de un socio del gym {}: {}", gymId, e.getMessage());
                out.add(new FixedClassImportMemberResult(email, name, false, "No se pudo cargar: " + e.getMessage(), 0, 0));
            }
        }
        return out;
    }

    private static String describeSaved(FixedClassesSaveResponse saved) {
        if (saved.noActivePlan()) {
            return "Sin plan activo: guardadas, se reservan cuando pague.";
        }
        String base = saved.created() + (saved.created() == 1 ? " reserva creada" : " reservas creadas");
        if (!saved.skipped().isEmpty()) {
            base += " · " + saved.skipped().size() + (saved.skipped().size() == 1 ? " fecha saltada" : " fechas saltadas");
        }
        if (saved.stoppedByQuota()) {
            base += " · se completó el cupo del plan";
        }
        return base;
    }

    // ---------------------------------------------------------------------------------------
    // Validación
    // ---------------------------------------------------------------------------------------

    private record Analysis(
            List<FixedClassRowResult> results, Map<String, AppUser> membersByEmail, Map<Integer, Long> blockIdByRow) {}

    private Analysis analyze(Long gymId, List<FixedClassImportRow> rows) {
        Map<String, AppUser> membersByEmail = appUserRepository.findByGymIdAndRole(gymId, Role.MEMBER).stream()
                .collect(Collectors.toMap(u -> AppUser.normalizeEmail(u.getEmail()), u -> u, (a, b) -> a));
        List<GymBlock> activeBlocks =
                gymBlockRepository.findByGymId(gymId).stream().filter(GymBlock::isActive).toList();
        Map<Long, GymBlock> blocksById = activeBlocks.stream().collect(Collectors.toMap(GymBlock::getId, b -> b));

        String[] status = new String[rows.size()];
        String[] message = new String[rows.size()];
        Map<Integer, Long> blockIdByRow = new HashMap<>();

        // 1) Fila por fila: socio y clase (día + hora + nombre).
        for (int i = 0; i < rows.size(); i++) {
            FixedClassImportRow row = rows.get(i);
            AppUser member = membersByEmail.get(AppUser.normalizeEmail(row.email()));
            if (member == null) {
                fail(status, message, i, "No hay un socio con ese email en tu gimnasio. Agrégalo primero en Socios.");
                continue;
            }
            String className = row.className() == null ? "" : row.className().trim();
            if (className.isEmpty()) {
                fail(status, message, i, "Falta el nombre de la clase. Cópialo de la hoja 'Clases del gimnasio'.");
                continue;
            }
            List<GymBlock> sameSlot = activeBlocks.stream()
                    .filter(b -> b.getDayOfWeek() == row.dayOfWeek() && sameMinute(b.getStartTime(), row.startTime()))
                    .toList();
            List<GymBlock> matches = sameSlot.stream()
                    .filter(b -> normalizeName(b.getLabel()).equals(normalizeName(className)))
                    .toList();
            String when = FixedClassFormat.dayLong(row.dayOfWeek()) + " a las " + row.startTime().format(HOUR);
            if (matches.isEmpty()) {
                fail(status, message, i, sameSlot.isEmpty()
                        ? "No hay clases el " + when + "."
                        : "No hay una clase '" + className + "' el " + when + ". A esa hora hay: "
                                + sameSlot.stream().map(GymBlock::getLabel).collect(Collectors.joining(", ")) + ".");
                continue;
            }
            if (matches.size() > 1) {
                fail(status, message, i, "Hay " + matches.size() + " clases '" + className + "' el " + when
                        + ". Revisa tus horarios.");
                continue;
            }
            blockIdByRow.put(i, matches.get(0).getId());
            status[i] = OK;
        }

        // 2) Por socio: una clase fija por día de la semana y sin filas repetidas.
        Map<String, Set<Long>> blocksSeen = new HashMap<>();
        Map<String, Map<DayOfWeek, GymBlock>> daySeen = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            if (!OK.equals(status[i])) {
                continue;
            }
            String email = AppUser.normalizeEmail(rows.get(i).email());
            GymBlock block = blocksById.get(blockIdByRow.get(i));
            if (!blocksSeen.computeIfAbsent(email, k -> new HashSet<>()).add(block.getId())) {
                fail(status, message, i, "Fila repetida: " + FixedClassFormat.describeShort(block) + " ya aparece arriba para este socio.");
                continue;
            }
            GymBlock sameDay = daySeen.computeIfAbsent(email, k -> new HashMap<>()).putIfAbsent(block.getDayOfWeek(), block);
            if (sameDay != null) {
                fail(status, message, i, "Solo puede tener una clase fija por día: el "
                        + FixedClassFormat.dayLong(block.getDayOfWeek()) + " ya tiene " + FixedClassFormat.describeShort(sameDay) + ".");
            }
        }

        // 3) Si un socio tiene alguna fila con error, se omiten TODAS sus filas.
        Set<String> emailsWithError = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            if (ERROR.equals(status[i])) {
                emailsWithError.add(AppUser.normalizeEmail(rows.get(i).email()));
            }
        }
        for (int i = 0; i < rows.size(); i++) {
            if (OK.equals(status[i]) && emailsWithError.contains(AppUser.normalizeEmail(rows.get(i).email()))) {
                status[i] = OMITTED;
                message[i] = "Se omite a este socio: corrige sus filas con error y vuelve a subir el archivo.";
            }
        }

        // 4) Advertencias (no impiden cargar): sin plan activo, clase llena, reemplaza clases actuales.
        List<Long> memberIds = membersByEmail.values().stream().map(AppUser::getId).toList();
        Map<Long, List<MemberFixedClass>> currentByMember = memberIds.isEmpty()
                ? Map.of()
                : fixedClassRepository.findByMemberIdIn(memberIds).stream()
                        .collect(Collectors.groupingBy(MemberFixedClass::getMemberId));
        Map<String, Set<Long>> wantedByEmail = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            if (OK.equals(status[i])) {
                wantedByEmail.computeIfAbsent(AppUser.normalizeEmail(rows.get(i).email()), k -> new HashSet<>()).add(blockIdByRow.get(i));
            }
        }
        LocalDate today = LocalDate.now(GYM_ZONE);
        Map<Long, Boolean> activePlanCache = new HashMap<>();
        Map<String, Integer> takenCache = new HashMap<>(); // una cuenta de cupo por clase+fecha, no por fila
        Set<String> replaceWarned = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            if (!OK.equals(status[i])) {
                continue;
            }
            String email = AppUser.normalizeEmail(rows.get(i).email());
            AppUser member = membersByEmail.get(email);
            GymBlock block = blocksById.get(blockIdByRow.get(i));
            List<String> warnings = new ArrayList<>();

            if (activePlanCache.computeIfAbsent(member.getId(), id -> memberService.hasActiveMembership(member))) {
                LocalDate next = nextOccurrence(today, block.getDayOfWeek());
                int taken = takenCache.computeIfAbsent(block.getId() + "|" + next, k ->
                        reservationRepository.countByGymBlockIdAndClassDateAndStatus(block.getId(), next, ReservationStatus.BOOKED));
                if (taken >= block.getCapacity()) {
                    warnings.add("La clase del " + FixedClassFormat.dayLong(block.getDayOfWeek()) + " ya está llena: se reservará igual, con sobrecupo.");
                }
            } else if (replaceWarned.add("plan|" + email)) {
                warnings.add("No tiene plan activo: se guardan sus clases fijas y se reservan cuando pague.");
            }

            List<MemberFixedClass> current = currentByMember.getOrDefault(member.getId(), List.of());
            Set<Long> currentBlocks = current.stream().map(MemberFixedClass::getGymBlockId).collect(Collectors.toSet());
            if (!current.isEmpty() && !currentBlocks.equals(wantedByEmail.get(email)) && replaceWarned.add("replace|" + email)) {
                String actual = FixedClassFormat.summary(gymBlockRepository.findAllById(currentBlocks));
                warnings.add("Reemplaza sus clases fijas actuales" + (actual != null ? " (" + actual + ")" : "") + ".");
            }

            if (!warnings.isEmpty()) {
                status[i] = WARNING;
                message[i] = String.join(" ", warnings);
            } else {
                message[i] = FixedClassFormat.describeShort(block);
            }
        }

        List<FixedClassRowResult> results = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            AppUser member = membersByEmail.get(AppUser.normalizeEmail(rows.get(i).email()));
            results.add(new FixedClassRowResult(i, rows.get(i).email(), member != null ? member.getName() : null, status[i], message[i]));
        }
        return new Analysis(results, membersByEmail, blockIdByRow);
    }

    private static void fail(String[] status, String[] message, int i, String text) {
        status[i] = ERROR;
        message[i] = text;
    }

    private static boolean sameMinute(LocalTime a, LocalTime b) {
        return a.getHour() == b.getHour() && a.getMinute() == b.getMinute();
    }

    private static LocalDate nextOccurrence(LocalDate from, DayOfWeek day) {
        LocalDate date = from;
        while (date.getDayOfWeek() != day) {
            date = date.plusDays(1);
        }
        return date;
    }

    /** Sin tildes, sin mayúsculas, espacios colapsados: "  CrossFit " == "crossfit". */
    static String normalizeName(String value) {
        String noAccents = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.trim().replaceAll("\\s+", " ").toLowerCase();
    }
}
