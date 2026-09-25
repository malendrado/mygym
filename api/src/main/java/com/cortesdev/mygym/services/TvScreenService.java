package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.GymPlan;
import com.cortesdev.mygym.models.TvPairingCode;
import com.cortesdev.mygym.models.TvScreen;
import com.cortesdev.mygym.models.dto.GymPhotoResponse;
import com.cortesdev.mygym.models.dto.MemberReservation;
import com.cortesdev.mygym.models.dto.TvAttendeeResponse;
import com.cortesdev.mygym.models.dto.TvBlockOccurrenceResponse;
import com.cortesdev.mygym.models.dto.TvPairingCreateResponse;
import com.cortesdev.mygym.models.dto.TvPairingStatusResponse;
import com.cortesdev.mygym.models.dto.TvScheduleResponse;
import com.cortesdev.mygym.models.dto.TvScreenResponse;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymPhotoRepository;
import com.cortesdev.mygym.repositories.GymPlanRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.TvPairingCodeRepository;
import com.cortesdev.mygym.repositories.TvScreenRepository;
import com.cortesdev.mygym.services.exception.GymNotFoundException;
import com.cortesdev.mygym.services.exception.TvPairingCodeNotFoundException;
import com.cortesdev.mygym.services.exception.TvScreenNotFoundException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pantallas de TV con el horario en vivo del gym — ver migración V23. Emparejamiento en dos
 * pasos, mismo patrón que Netflix/YouTube al abrir la app en una TV nueva: la TV pide un código
 * corto ({@link #createPairingCode()}), lo muestra en pantalla y hace poll de su estado
 * ({@link #pairingStatus}); el admin, desde su panel ya autenticado, lo reclama
 * ({@link #claimPairingCode}) — recién ahí la TV recibe su token real y empieza a pedir datos de
 * verdad ({@link #getSchedule}).
 */
@Service
@RequiredArgsConstructor
public class TvScreenService {

    private static final ZoneId GYM_ZONE = ZoneId.of("America/Santiago");

    private static final Duration PAIRING_CODE_TTL = Duration.ofMinutes(10);

    // A diferencia del acceso a la demo comercial (48h fijas, corte duro) — una TV es un
    // dispositivo que se instala una vez y queda prendido meses/años. No tiene sentido un
    // vencimiento por tiempo fijo acá; en cambio, se apaga sola "por abandono" si deja de hacer
    // poll (se rompió, se desenchufó para siempre, el gym cerró) — así no queda un acceso
    // fantasma vivo para siempre si alguien se olvida de desvincularla a mano.
    private static final Duration SCREEN_INACTIVITY_TTL = Duration.ofDays(90);

    // Sin 0/O/1/I — se leen fácil desde lejos en una tele y no se confunden entre sí.
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    // Tope de la agenda "más tarde" — el frontend igual muestra solo las filas que entran
    // medidas en pantalla, con un "+N" si sobran.
    private static final int LATER_LIMIT = 8;


    private final TvPairingCodeRepository pairingCodeRepository;
    private final TvScreenRepository tvScreenRepository;
    private final GymRepository gymRepository;
    private final GymBlockRepository gymBlockRepository;
    private final GymPhotoRepository gymPhotoRepository;
    private final GymPlanRepository gymPlanRepository;
    private final ReservationService reservationService;

    public TvPairingCreateResponse createPairingCode() {
        Instant now = Instant.now();
        TvPairingCode pairing = pairingCodeRepository.save(TvPairingCode.builder()
                .code(generateUniqueCode())
                .createdAt(now)
                .expiresAt(now.plus(PAIRING_CODE_TTL))
                .build());
        return new TvPairingCreateResponse(pairing.getCode(), pairing.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public TvPairingStatusResponse pairingStatus(String code) {
        TvPairingCode pairing = findValidPairing(code);
        return new TvPairingStatusResponse(pairing.getClaimedAt() != null, pairing.getScreenToken());
    }

    public TvScreenResponse claimPairingCode(Long gymId, String code, String name) {
        TvPairingCode pairing = pairingCodeRepository
                .findByCode(code)
                .filter(p -> p.getExpiresAt().isAfter(Instant.now()))
                .filter(p -> p.getClaimedAt() == null)
                .orElseThrow(() -> new TvPairingCodeNotFoundException(code));

        TvScreen screen = tvScreenRepository.save(TvScreen.builder()
                .gymId(gymId)
                .name(name)
                .token(generateToken())
                .createdAt(Instant.now())
                .build());

        pairing.setClaimedAt(Instant.now());
        pairing.setScreenToken(screen.getToken());
        pairingCodeRepository.save(pairing);

        return toResponse(screen);
    }

    @Transactional(readOnly = true)
    public List<TvScreenResponse> listScreens(Long gymId) {
        return tvScreenRepository.findByGymId(gymId).stream().map(this::toResponse).toList();
    }

    public void deleteScreen(Long gymId, Long id) {
        TvScreen screen = tvScreenRepository.findByIdAndGymId(id, gymId).orElseThrow(() -> new TvScreenNotFoundException(id));
        tvScreenRepository.delete(screen);
    }

    public TvScheduleResponse getSchedule(String token) {
        TvScreen screen = tvScreenRepository
                .findByToken(token)
                .filter(this::isActive)
                .orElseThrow(() -> new TvScreenNotFoundException(token));
        screen.setLastPolledAt(Instant.now());
        tvScreenRepository.save(screen);

        Gym gym = gymRepository.findById(screen.getGymId()).orElseThrow(() -> new GymNotFoundException(screen.getGymId()));
        ZonedDateTime nowZoned = ZonedDateTime.now(GYM_ZONE);

        List<GymBlock> activeBlocks =
                gymBlockRepository.findByGymId(gym.getId()).stream().filter(GymBlock::isActive).toList();

        List<GymBlock> currentBlocks = activeBlocks.stream()
                .filter(b -> b.getDayOfWeek() == nowZoned.getDayOfWeek())
                .filter(b -> !nowZoned.toLocalTime().isBefore(b.getStartTime())
                        && nowZoned.toLocalTime().isBefore(b.getEndTime()))
                .sorted(Comparator.comparing(GymBlock::getStartTime))
                .toList();

        NextOccurrence next = findNextOccurrence(activeBlocks, nowZoned);

        List<GymPhotoResponse> photos = gymPhotoRepository.findByGymIdOrderBySortOrderAsc(gym.getId()).stream()
                .map(p -> new GymPhotoResponse(p.getId(), p.getData(), p.getCaption()))
                .toList();

        List<GymPlan> gymPlans = gymPlanRepository.findByGymId(gym.getId());
        Map<Long, GymPlan> plansById = gymPlans.stream().collect(java.util.stream.Collectors.toMap(GymPlan::getId, p -> p));

        // La clase de hoy que terminó más recientemente (si ya hubo alguna) — layout fijo:
        // "anterior" es un solo casillero, no una lista rotativa.
        GymBlock previousBlock = activeBlocks.stream()
                .filter(b -> b.getDayOfWeek() == nowZoned.getDayOfWeek())
                .filter(b -> b.getEndTime().isBefore(nowZoned.toLocalTime()))
                .max(Comparator.comparing(GymBlock::getStartTime))
                .orElse(null);

        return new TvScheduleResponse(
                gym.getName(),
                gym.getLogoSvg(),
                gym.getThemeColor(),
                gym.getThemeMode(),
                gym.getTagline(),
                photos,
                Instant.now(),
                currentBlocks.stream().map(b -> toOccurrence(gym.getId(), b, nowZoned.toLocalDate(), plansById)).toList(),
                next == null
                        ? List.of()
                        : next.blocks().stream().map(b -> toOccurrence(gym.getId(), b, next.date(), plansById)).toList(),
                next == null ? null : next.date(),
                previousBlock == null ? null : toOccurrence(gym.getId(), previousBlock, nowZoned.toLocalDate(), plansById),
                next == null
                        ? List.of()
                        : next.later().stream().map(b -> toOccurrence(gym.getId(), b, next.date(), plansById)).toList());
    }

    private boolean isActive(TvScreen screen) {
        Instant reference = screen.getLastPolledAt() != null ? screen.getLastPolledAt() : screen.getCreatedAt();
        return reference.isAfter(Instant.now().minus(SCREEN_INACTIVITY_TTL));
    }

    // later: el resto de las clases de ESE mismo día después del horario de "próxima" — la
    // agenda "más tarde" de la pantalla (práctica estándar de señalética de gimnasios: mostrar
    // las próximas horas, no solo la siguiente clase).
    private record NextOccurrence(LocalDate date, List<GymBlock> blocks, List<GymBlock> later) {}

    // Busca, desde hoy y hasta 7 días hacia adelante, el/los bloque(s) con el startTime más
    // próximo que todavía no arrancó — puede haber más de uno si hay clases simultáneas (mismo
    // día, misma hora, dos instructores/categorías distintas), ver punto 2 del diseño.
    private NextOccurrence findNextOccurrence(List<GymBlock> activeBlocks, ZonedDateTime nowZoned) {
        for (int dayOffset = 0; dayOffset <= 7; dayOffset++) {
            LocalDate candidateDate = nowZoned.toLocalDate().plusDays(dayOffset);
            boolean isToday = dayOffset == 0;
            List<GymBlock> dayBlocks = activeBlocks.stream()
                    .filter(b -> b.getDayOfWeek() == candidateDate.getDayOfWeek())
                    .filter(b -> !isToday || b.getStartTime().isAfter(nowZoned.toLocalTime()))
                    .sorted(Comparator.comparing(GymBlock::getStartTime))
                    .toList();
            if (!dayBlocks.isEmpty()) {
                var earliest = dayBlocks.get(0).getStartTime();
                List<GymBlock> simultaneous =
                        dayBlocks.stream().filter(b -> b.getStartTime().equals(earliest)).toList();
                List<GymBlock> later = dayBlocks.stream()
                        .filter(b -> b.getStartTime().isAfter(earliest))
                        .limit(LATER_LIMIT)
                        .toList();
                return new NextOccurrence(candidateDate, simultaneous, later);
            }
        }
        return null;
    }

    private TvBlockOccurrenceResponse toOccurrence(Long gymId, GymBlock block, LocalDate classDate, Map<Long, GymPlan> plansById) {
        List<TvAttendeeResponse> attendees = reservationService.getOccurrenceAttendees(gymId, block.getId(), classDate)
                .stream()
                .map(mr -> toSummary(mr, plansById))
                .toList();
        return new TvBlockOccurrenceResponse(
                block.getId(),
                block.getLabel(),
                block.getCategory(),
                block.getInstructorName(),
                block.getInstructorPhoto(),
                block.getStartTime(),
                block.getEndTime(),
                classDate,
                block.getCapacity(),
                attendees.size(),
                attendees);
    }

    // Misma regla de privacidad que ReservationController.toAttendeeSummary — nunca email ni
    // apellido, la pantalla es pública en el sentido de que cualquiera que la vea físicamente
    // en el gym puede leerla. planName/planId sí se agregan acá (a diferencia de
    // AttendeeSummaryResponse) porque el usuario pidió explícitamente un identificador visual
    // del plan en el roster de la TV — AppUser.planId no es una relación JPA, se resuelve a
    // mano contra plansById. planId viaja aparte para que el frontend le asigne un color
    // estable por id, no por texto.
    private TvAttendeeResponse toSummary(MemberReservation mr, Map<Long, GymPlan> plansById) {
        String name = mr.member().getName();
        String[] tokens = name == null ? new String[0] : name.trim().split("\\s+");
        String firstName = tokens.length == 0 ? "Socio" : tokens[0];
        String lastName = tokens.length > 1 ? String.join(" ", Arrays.copyOfRange(tokens, 1, tokens.length)) : null;
        Long planId = mr.member().getPlanId();
        GymPlan plan = planId == null ? null : plansById.get(planId);
        return new TvAttendeeResponse(
                firstName, lastName, mr.member().getPhotoUrl(), plan == null ? null : plan.getName(), plan == null ? null : plan.getId());
    }

    private TvPairingCode findValidPairing(String code) {
        return pairingCodeRepository
                .findByCode(code)
                .filter(p -> p.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new TvPairingCodeNotFoundException(code));
    }

    private TvScreenResponse toResponse(TvScreen screen) {
        return new TvScreenResponse(screen.getId(), screen.getName(), screen.getCreatedAt(), screen.getLastPolledAt());
    }

    private String generateUniqueCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            code = sb.toString();
        } while (pairingCodeRepository.findByCode(code).isPresent());
        return code;
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
