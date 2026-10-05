package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.DemoTourProgress;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.GymPhoto;
import com.cortesdev.mygym.models.GymPlan;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.AdminCreateRequest;
import com.cortesdev.mygym.models.dto.AdminResponse;
import com.cortesdev.mygym.models.dto.AdminStatusUpdateRequest;
import com.cortesdev.mygym.models.dto.BlockCreateRequest;
import com.cortesdev.mygym.models.dto.BlockCreateResult;
import com.cortesdev.mygym.models.dto.BlockResponse;
import com.cortesdev.mygym.models.dto.BlockUpdateRequest;
import com.cortesdev.mygym.models.dto.GymConfigUpdateRequest;
import com.cortesdev.mygym.models.dto.GymCreateRequest;
import com.cortesdev.mygym.models.dto.BankTransferInfoResponse;
import com.cortesdev.mygym.models.dto.BankTransferUpdateRequest;
import com.cortesdev.mygym.models.dto.FlowAccountDetailsUpdateRequest;
import com.cortesdev.mygym.models.dto.FlowAccountResponse;
import com.cortesdev.mygym.models.dto.FlowAccountUpdateRequest;
import com.cortesdev.mygym.models.dto.GymIdentityUpdateRequest;
import com.cortesdev.mygym.models.dto.GymPhotoCreateRequest;
import com.cortesdev.mygym.models.dto.GymPhotoResponse;
import com.cortesdev.mygym.models.dto.GymResponse;
import com.cortesdev.mygym.models.dto.GymSummaryResponse;
import com.cortesdev.mygym.models.dto.MemberPlanResponse;
import com.cortesdev.mygym.models.dto.PlanCreateRequest;
import com.cortesdev.mygym.models.dto.PlanResponse;
import com.cortesdev.mygym.models.dto.PlanUpdateRequest;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.dto.PublicGymResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymPhotoRepository;
import com.cortesdev.mygym.repositories.GymPlanRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.services.exception.AdminNotFoundException;
import com.cortesdev.mygym.services.exception.DuplicateOwnerEmailException;
import com.cortesdev.mygym.services.exception.DuplicateSlugException;
import com.cortesdev.mygym.services.exception.GymBlockNotFoundException;
import com.cortesdev.mygym.services.exception.GymNotFoundException;
import com.cortesdev.mygym.services.exception.GymPhotoNotFoundException;
import com.cortesdev.mygym.services.exception.GymPlanNotFoundException;
import com.cortesdev.mygym.services.exception.InvalidBlockScheduleException;
import com.cortesdev.mygym.services.exception.InvalidLogoException;
import com.cortesdev.mygym.services.exception.InvalidRutException;
import com.cortesdev.mygym.services.exception.InvalidThemeException;
import com.cortesdev.mygym.services.exception.MemberNotFoundException;
import com.cortesdev.mygym.services.exception.ProfesorNotFoundException;
import com.cortesdev.mygym.services.exception.TooManyGymPhotosException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class GymService {

    // Mismo huso que usa MemberService para calcular vigencia de plan — acá
    // define qué reservas cuentan como "a futuro" al expulsar a un socio.
    private static final ZoneId GYM_ZONE = ZoneId.of("America/Santiago");

    private final GymRepository gymRepository;
    private final GymBlockRepository gymBlockRepository;
    private final GymPlanRepository gymPlanRepository;
    private final GymPhotoRepository gymPhotoRepository;
    private final AppUserRepository appUserRepository;
    private final ReservationRepository reservationRepository;
    private final AdminInviteEmailService adminInviteEmailService;
    private final MemberLifecycleEmailService memberLifecycleEmailService;
    private final DemoTourService demoTourService;

    public GymResponse createGym(GymCreateRequest request) {
        if (gymRepository.existsBySlug(request.slug())) {
            throw new DuplicateSlugException(request.slug());
        }
        if (appUserRepository.existsByEmail(AppUser.normalizeEmail(request.ownerEmail()))) {
            throw new DuplicateOwnerEmailException(request.ownerEmail());
        }
        Gym gym = Gym.builder()
                .name(request.name())
                .slug(request.slug())
                .maxUsers(request.maxUsers())
                .active(true)
                .googleLoginEnabled(true)
                .themeColor(request.themeColor())
                .logoSvg(request.logoSvg())
                .cancellationWindowHours(2)
                .build();
        gym = gymRepository.save(gym);

        AppUser owner = AppUser.builder()
                .email(request.ownerEmail())
                .name(request.ownerName())
                .role(Role.GYM_ADMIN)
                .gymId(gym.getId())
                .active(true)
                .build();
        owner = appUserRepository.save(owner);
        adminInviteEmailService.sendGymOwnerInvite(gym, owner);

        return toResponse(gym);
    }

    @Transactional(readOnly = true)
    public List<GymSummaryResponse> listGyms(Boolean active) {
        return gymRepository.findAllSummaries(active);
    }

    @Transactional(readOnly = true)
    public GymResponse getGym(Long id) {
        return toResponse(findGymOrThrow(id));
    }

    /** Resuelve el gym por el UUID opaco de la URL del super-admin (nunca por el id secuencial). */
    @Transactional(readOnly = true)
    public GymResponse getGymByPublicId(java.util.UUID publicId) {
        Gym gym = gymRepository.findByPublicId(publicId).orElseThrow(() -> new GymNotFoundException(publicId));
        return toResponse(gym);
    }

    @Transactional(readOnly = true)
    public PublicGymResponse getPublicBySlug(String slug) {
        Gym gym = gymRepository.findBySlug(slug).orElseThrow(() -> new GymNotFoundException(slug));
        if (!gym.isActive()) {
            throw new GymNotFoundException(slug);
        }
        return toPublicResponse(gym);
    }

    /** Branding for the logged-in member's own gym (mygym.cl "member" portal) — gymId comes from the caller's JWT. */
    @Transactional(readOnly = true)
    public PublicGymResponse getPublicById(Long gymId) {
        return toPublicResponse(findGymOrThrow(gymId));
    }

    private PublicGymResponse toPublicResponse(Gym gym) {
        String themeColor = gym.getThemeColor() != null ? gym.getThemeColor() : GymPalette.defaultHex();
        return new PublicGymResponse(
                gym.getName(),
                gym.getSlug(),
                themeColor,
                GymPalette.contrastFor(themeColor),
                gym.getThemeMode(),
                gym.getLogoSvg(),
                gym.isGoogleLoginEnabled(),
                gym.getTagline(),
                gym.getDescription(),
                gym.getInstagramUrl(),
                gym.getWhatsappNumber(),
                gym.getCancellationWindowHours(),
                hasFlowCredentials(gym));
    }

    public GymResponse updateGymConfig(Long id, GymConfigUpdateRequest request) {
        Gym gym = findGymOrThrow(id);
        gym.setActive(request.active());
        gym.setMaxUsers(request.maxUsers());
        gym.setGoogleLoginEnabled(request.googleLoginEnabled());
        gym.setLogoSvg(request.logoSvg());
        return toResponse(gymRepository.save(gym));
    }

    // "LIGHT" está limitado a las 4 paletas curadas de GymPalette.LIGHT_ALL — a
    // diferencia de "DARK" (acento libre), no alcanza con validar el formato del hex
    // en el DTO, hay que validar que sea exactamente uno de los 4 colores conocidos
    // (evita que alguien mande un color libre en modo claro, sin superficie/contraste
    // verificados para ese tono).
    public void updateTheme(Long gymId, String themeColor, String themeMode) {
        if ("LIGHT".equals(themeMode) && GymPalette.LIGHT_ALL.stream().noneMatch(e -> e.hex().equalsIgnoreCase(themeColor))) {
            throw new InvalidThemeException("El modo claro solo admite una de las paletas curadas, no un color libre");
        }
        Gym gym = findGymOrThrow(gymId);
        gym.setThemeColor(themeColor);
        gym.setThemeMode(themeMode);
        gymRepository.save(gym);
    }

    public void updateIdentity(Long gymId, GymIdentityUpdateRequest request) {
        Gym gym = findGymOrThrow(gymId);
        gym.setTagline(request.tagline());
        gym.setDescription(request.description());
        gym.setInstagramUrl(request.instagramUrl());
        gym.setWhatsappNumber(request.whatsappNumber());
        gym.setCancellationWindowHours(request.cancellationWindowHours());
        gymRepository.save(gym);
    }

    public void updateBankTransfer(Long gymId, BankTransferUpdateRequest request) {
        Gym gym = findGymOrThrow(gymId);
        gym.setBankName(blankToNull(request.bankName()));
        gym.setBankAccountType(blankToNull(request.accountType()));
        gym.setBankAccountNumber(blankToNull(request.accountNumber()));
        gym.setBankHolderRut(validatedRutOrNull(request.holderRut(), "RUT titular"));
        gym.setBankHolderName(blankToNull(request.holderName()));
        gym.setBankConfirmationEmail(blankToNull(request.confirmationEmail()));
        gymRepository.save(gym);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** Valida y normaliza un RUT chileno (empresa o representante legal) al formato
     *  "XX.XXX.XXX-D" antes de guardarlo — mismo criterio que el validador del frontend
     *  (rut.ts), acá como defensa en profundidad para quien llame la API directo. */
    private static String validatedRutOrNull(String value, String fieldLabel) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            return null;
        }
        if (!RutValidator.isValid(trimmed)) {
            throw new InvalidRutException(fieldLabel);
        }
        return RutValidator.format(trimmed);
    }

    /** Vista del socio de los datos bancarios de SU gym — "configurado" exige los 4 campos
     *  clave (banco, tipo de cuenta, número, titular); sin eso, /member oculta la opción de
     *  transferencia en vez de mostrar un desglose incompleto. */
    @Transactional(readOnly = true)
    public BankTransferInfoResponse getBankTransferInfo(Long gymId) {
        Gym gym = findGymOrThrow(gymId);
        boolean configured = gym.getBankName() != null
                && gym.getBankAccountType() != null
                && gym.getBankAccountNumber() != null
                && gym.getBankHolderRut() != null
                && gym.getBankHolderName() != null;
        if (!configured) {
            return new BankTransferInfoResponse(false, null, null, null, null, null, null);
        }
        return new BankTransferInfoResponse(
                true,
                gym.getBankName(),
                gym.getBankAccountType(),
                gym.getBankAccountNumber(),
                gym.getBankHolderRut(),
                gym.getBankHolderName(),
                gym.getBankConfirmationEmail());
    }

    /** Vista del super-admin de la cuenta Pago Online (Flow.cl) — ver GymController. */
    @Transactional(readOnly = true)
    public FlowAccountResponse getFlowAccount(Long gymId) {
        return toFlowAccountResponse(findGymOrThrow(gymId));
    }

    public FlowAccountResponse updateFlowAccount(Long gymId, FlowAccountUpdateRequest request) {
        Gym gym = findGymOrThrow(gymId);
        gym.setFlowCompanyRut(validatedRutOrNull(request.companyRut(), "RUT de la empresa"));
        gym.setFlowCompanyName(blankToNull(request.companyName()));
        gym.setFlowBusinessActivity(blankToNull(request.businessActivity()));
        gym.setFlowCompanyAddress(blankToNull(request.companyAddress()));
        gym.setFlowVatCondition(blankToNull(request.vatCondition()));
        gym.setFlowLegalRepName(blankToNull(request.legalRepName()));
        gym.setFlowLegalRepRut(validatedRutOrNull(request.legalRepRut(), "RUT del representante legal"));
        gym.setFlowLegalRepPhone(blankToNull(request.legalRepPhone()));
        gym.setFlowContactEmail(blankToNull(request.contactEmail()));
        gym.setFlowContactName(blankToNull(request.contactName()));
        gym.setFlowContactPhone(blankToNull(request.contactPhone()));
        // apiKey/secretKey: solo se sobreescriben si llega un valor no vacío — nunca se
        // devuelven en claro al frontend, así que un vacío significa "no tocar" (ver DTO).
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            gym.setFlowApiKey(request.apiKey().strip());
        }
        if (request.secretKey() != null && !request.secretKey().isBlank()) {
            gym.setFlowSecretKey(request.secretKey().strip());
        }
        return toFlowAccountResponse(gymRepository.save(gym));
    }

    /** Lo que puede tocar el propio GYM_ADMIN de su cuenta Pago Online — todo salvo
     *  apiKey/secretKey (ver FlowAccountDetailsUpdateRequest), que solo carga el
     *  super-admin una vez que estos datos ya están completos. */
    public FlowAccountResponse updateFlowAccountDetails(Long gymId, FlowAccountDetailsUpdateRequest request) {
        Gym gym = findGymOrThrow(gymId);
        gym.setFlowCompanyRut(validatedRutOrNull(request.companyRut(), "RUT de la empresa"));
        gym.setFlowCompanyName(blankToNull(request.companyName()));
        gym.setFlowBusinessActivity(blankToNull(request.businessActivity()));
        gym.setFlowCompanyAddress(blankToNull(request.companyAddress()));
        gym.setFlowVatCondition(blankToNull(request.vatCondition()));
        gym.setFlowLegalRepName(blankToNull(request.legalRepName()));
        gym.setFlowLegalRepRut(validatedRutOrNull(request.legalRepRut(), "RUT del representante legal"));
        gym.setFlowLegalRepPhone(blankToNull(request.legalRepPhone()));
        gym.setFlowContactEmail(blankToNull(request.contactEmail()));
        gym.setFlowContactName(blankToNull(request.contactName()));
        gym.setFlowContactPhone(blankToNull(request.contactPhone()));
        return toFlowAccountResponse(gymRepository.save(gym));
    }

    private FlowAccountResponse toFlowAccountResponse(Gym gym) {
        return new FlowAccountResponse(
                hasFlowCredentials(gym),
                gym.getFlowCompanyRut(),
                gym.getFlowCompanyName(),
                gym.getFlowBusinessActivity(),
                gym.getFlowCompanyAddress(),
                gym.getFlowVatCondition(),
                gym.getFlowLegalRepName(),
                gym.getFlowLegalRepRut(),
                gym.getFlowLegalRepPhone(),
                gym.getFlowContactEmail(),
                gym.getFlowContactName(),
                gym.getFlowContactPhone(),
                gym.getFlowApiKey() != null,
                gym.getFlowSecretKey() != null,
                maskSecret(gym.getFlowApiKey()),
                maskSecret(gym.getFlowSecretKey()));
    }

    private static boolean hasFlowCredentials(Gym gym) {
        return gym.getFlowApiKey() != null
                && !gym.getFlowApiKey().isBlank()
                && gym.getFlowSecretKey() != null
                && !gym.getFlowSecretKey().isBlank();
    }

    private static String maskSecret(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String last4 = value.length() <= 4 ? value : value.substring(value.length() - 4);
        return "••••" + last4;
    }

    private static final List<String> ALLOWED_LOGO_IMAGE_MIME_TYPES =
            List.of("data:image/png", "data:image/jpeg", "data:image/webp");

    public void updateMyLogo(Long gymId, String logo) {
        Gym gym = findGymOrThrow(gymId);
        String trimmed = logo.strip();
        if (SvgSanitizer.looksLikeSvg(trimmed)) {
            String error = SvgSanitizer.validate(trimmed);
            if (error != null) {
                throw new InvalidLogoException(error);
            }
        } else if (trimmed.toLowerCase(Locale.ROOT).startsWith("data:image/")) {
            boolean allowedMime = ALLOWED_LOGO_IMAGE_MIME_TYPES.stream()
                    .anyMatch(prefix -> trimmed.toLowerCase(Locale.ROOT).startsWith(prefix));
            if (!allowedMime) {
                throw new InvalidLogoException("Formato de imagen no soportado. Usa PNG, JPG o WEBP.");
            }
        } else {
            throw new InvalidLogoException("El logo debe ser un SVG o una imagen (PNG, JPG, WEBP).");
        }
        gym.setLogoSvg(trimmed);
        gymRepository.save(gym);
    }

    private static final int MAX_GYM_PHOTOS = 8;

    @Transactional(readOnly = true)
    public List<GymPhotoResponse> listPhotos(Long gymId) {
        findGymOrThrow(gymId);
        return gymPhotoRepository.findByGymIdOrderBySortOrderAsc(gymId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Vista pública (mygym.cl/j/{slug}) — mismas fotos, sin auth. */
    @Transactional(readOnly = true)
    public List<GymPhotoResponse> listPublicPhotosBySlug(String slug) {
        Gym gym = gymRepository.findBySlug(slug).orElseThrow(() -> new GymNotFoundException(slug));
        if (!gym.isActive()) {
            throw new GymNotFoundException(slug);
        }
        return gymPhotoRepository.findByGymIdOrderBySortOrderAsc(gym.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    public GymPhotoResponse addPhoto(Long gymId, GymPhotoCreateRequest request) {
        findGymOrThrow(gymId);
        long existing = gymPhotoRepository.countByGymId(gymId);
        if (existing >= MAX_GYM_PHOTOS) {
            throw new TooManyGymPhotosException(MAX_GYM_PHOTOS);
        }
        String trimmed = request.data().strip();
        boolean allowedMime = ALLOWED_LOGO_IMAGE_MIME_TYPES.stream()
                .anyMatch(prefix -> trimmed.toLowerCase(Locale.ROOT).startsWith(prefix));
        if (!allowedMime) {
            throw new InvalidLogoException("La foto debe ser una imagen (PNG, JPG o WEBP).");
        }
        GymPhoto photo = GymPhoto.builder()
                .gymId(gymId)
                .data(trimmed)
                .caption(request.caption())
                .sortOrder((int) existing)
                .build();
        return toResponse(gymPhotoRepository.save(photo));
    }

    public void removePhoto(Long gymId, Long photoId) {
        findGymOrThrow(gymId);
        GymPhoto photo = gymPhotoRepository
                .findByIdAndGymId(photoId, gymId)
                .orElseThrow(() -> new GymPhotoNotFoundException(gymId, photoId));
        gymPhotoRepository.delete(photo);
    }

    private GymPhotoResponse toResponse(GymPhoto photo) {
        return new GymPhotoResponse(photo.getId(), photo.getData(), photo.getCaption());
    }

    @Transactional(readOnly = true)
    public List<AdminResponse> listAdmins(Long gymId) {
        findGymOrThrow(gymId);
        return appUserRepository.findByGymIdAndRole(gymId, Role.GYM_ADMIN).stream()
                .map(this::toResponse)
                .toList();
    }

    public AdminResponse addAdmin(Long gymId, AdminCreateRequest request) {
        Gym gym = findGymOrThrow(gymId);
        if (appUserRepository.existsByEmail(AppUser.normalizeEmail(request.email()))) {
            throw new DuplicateOwnerEmailException(request.email());
        }
        AppUser admin = AppUser.builder()
                .email(request.email())
                .name(request.name())
                .role(Role.GYM_ADMIN)
                .gymId(gymId)
                .active(true)
                .build();
        admin = appUserRepository.save(admin);
        adminInviteEmailService.sendAdminInvite(gym, admin);
        return toResponse(admin);
    }

    public AdminResponse updateAdminStatus(Long gymId, Long userId, AdminStatusUpdateRequest request) {
        AppUser admin = findAdminOrThrow(gymId, userId);
        admin.setActive(request.active());
        return toResponse(appUserRepository.save(admin));
    }

    // Borrado real, no desactivar — mismo bug que ya se había resuelto una vez para
    // removeDemoAdmin (ver comentario ahí), pero reportado de nuevo para un GYM_ADMIN real:
    // "Quitar acceso" solo pone active=false, así que el email queda ocupado para siempre por
    // existsByEmail() en addAdmin, sin forma de volver a invitar a esa persona (ni a ese gym ni
    // a ningún otro, el email es único en toda la tabla). Un GYM_ADMIN nunca tiene reservas ni
    // pagos propios (esas tablas son de socios), así que borrar el registro entero es seguro.
    public void removeAdmin(Long gymId, Long userId) {
        AppUser admin = appUserRepository
                .findByIdAndGymId(userId, gymId)
                .orElseThrow(() -> new AdminNotFoundException(gymId, userId));
        if (admin.getRole() != Role.GYM_ADMIN) {
            throw new AdminNotFoundException(gymId, userId);
        }
        appUserRepository.delete(admin);
    }

    // ---- Profesores de "Memoria Viva" (ver WorkoutService) — a diferencia de addAdmin (solo
    // SUPER_ADMIN, bajo /api/gyms/**), esto lo llama el propio GYM_ADMIN bajo /api/gym-admin/**,
    // mismo patrón que MemberService.createMember para invitar socios. Sin email de invitación
    // por ahora (simplificación a propósito para no duplicar plantillas HTML — el admin invitado
    // real sí recibe una, pensar esto si se pide más adelante).

    public AdminResponse addProfesor(Long gymId, AdminCreateRequest request) {
        if (appUserRepository.existsByEmail(AppUser.normalizeEmail(request.email()))) {
            throw new DuplicateOwnerEmailException(request.email());
        }
        AppUser profesor = AppUser.builder()
                .email(request.email())
                .name(request.name())
                .role(Role.PROFESOR)
                .gymId(gymId)
                .active(true)
                .build();
        return toResponse(appUserRepository.save(profesor));
    }

    public List<AdminResponse> listProfesores(Long gymId) {
        findGymOrThrow(gymId);
        return appUserRepository.findByGymIdAndRole(gymId, Role.PROFESOR).stream()
                .map(this::toResponse)
                .toList();
    }

    public void removeProfesor(Long gymId, Long userId) {
        AppUser profesor = appUserRepository
                .findByIdAndGymId(userId, gymId)
                .filter(u -> u.getRole() == Role.PROFESOR)
                .orElseThrow(() -> new ProfesorNotFoundException(gymId, userId));
        appUserRepository.delete(profesor);
    }

    // Acceso de solo-lectura a la demo comercial (ver Role.DEMO_ADMIN / SecurityConfig). Reusa
    // el mismo AppUser + email de invitación que un admin real, pero con otro rol y otra copia de
    // email — nunca se debe confundir con addAdmin, que da acceso de escritura real a un gym real.
    @Transactional(readOnly = true)
    public List<AdminResponse> listDemoAdmins(Long gymId) {
        findGymOrThrow(gymId);
        List<AppUser> demoAdmins = appUserRepository.findByGymIdAndRole(gymId, Role.DEMO_ADMIN);
        Map<Long, List<DemoTourProgress>> progressByAdmin =
                demoTourService.findProgressByAdminIds(demoAdmins.stream().map(AppUser::getId).toList());
        return demoAdmins.stream().map(admin -> toResponse(admin, progressByAdmin)).toList();
    }

    public AdminResponse addDemoAdmin(Long gymId, AdminCreateRequest request) {
        Gym gym = findGymOrThrow(gymId);
        if (appUserRepository.existsByEmail(AppUser.normalizeEmail(request.email()))) {
            throw new DuplicateOwnerEmailException(request.email());
        }
        AppUser demoAdmin = AppUser.builder()
                .email(request.email())
                .name(request.name())
                .role(Role.DEMO_ADMIN)
                .gymId(gymId)
                .active(true)
                .build();
        demoAdmin = appUserRepository.save(demoAdmin);
        adminInviteEmailService.sendDemoInvite(gym, demoAdmin);
        return toResponse(demoAdmin);
    }

    // Borrado real, no desactivar (a diferencia de updateAdminStatus para un GYM_ADMIN real):
    // el email de un acceso demo revocado tiene que quedar completamente libre para que esa
    // misma persona pueda convertirse en un admin real más adelante (bug real: dejaba el email
    // "ocupado" para siempre por existsByEmail, bloqueando la invitación real). Nunca borra
    // reservas/pagos porque un DEMO_ADMIN nunca los tiene — no es un socio.
    public void removeDemoAdmin(Long gymId, Long userId) {
        AppUser demoAdmin = appUserRepository
                .findByIdAndGymId(userId, gymId)
                .orElseThrow(() -> new AdminNotFoundException(gymId, userId));
        if (demoAdmin.getRole() != Role.DEMO_ADMIN) {
            throw new AdminNotFoundException(gymId, userId);
        }
        appUserRepository.delete(demoAdmin);
    }

    public BlockResponse addBlock(Long gymId, BlockCreateRequest request) {
        findGymOrThrow(gymId);
        validateSchedule(request.startTime(), request.endTime());
        GymBlock block = GymBlock.builder()
                .gymId(gymId)
                .label(request.label())
                .dayOfWeek(request.dayOfWeek())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .capacity(request.capacity())
                .category(request.category())
                .instructorName(request.instructorName())
                .instructorPhoto(request.instructorPhoto())
                .active(true)
                .build();
        return toResponse(gymBlockRepository.save(block));
    }

    // Reemplaza el fan-out de un POST por bloque que hacía el frontend al generar un horario en
    // serie (BloqueSeriesModal/confirmSeries) — un solo request con todos los bloques, en vez de
    // N round-trips secuenciales (podían ser ~80-300 según la configuración elegida). Cada
    // bloque sigue siendo independiente (try/catch por item, igual que MemberService.
    // importMembers): un horario inválido en uno no tumba el resto del lote.
    public List<BlockCreateResult> addBlocks(Long gymId, List<BlockCreateRequest> requests) {
        findGymOrThrow(gymId);
        List<BlockCreateResult> results = new ArrayList<>();
        for (BlockCreateRequest request : requests) {
            try {
                results.add(new BlockCreateResult(true, addBlock(gymId, request), null));
            } catch (InvalidBlockScheduleException e) {
                results.add(new BlockCreateResult(false, null, e.getMessage()));
            }
        }
        return results;
    }

    @Transactional(readOnly = true)
    public List<BlockResponse> listBlocks(Long gymId) {
        findGymOrThrow(gymId);
        return gymBlockRepository.findByGymId(gymId).stream().map(this::toResponse).toList();
    }

    public BlockResponse updateBlock(Long gymId, Long blockId, BlockUpdateRequest request) {
        findGymOrThrow(gymId);
        GymBlock block = findBlockOrThrow(gymId, blockId);
        validateSchedule(request.startTime(), request.endTime());
        block.setLabel(request.label());
        block.setDayOfWeek(request.dayOfWeek());
        block.setStartTime(request.startTime());
        block.setEndTime(request.endTime());
        block.setCapacity(request.capacity());
        block.setCategory(request.category());
        block.setInstructorName(request.instructorName());
        block.setInstructorPhoto(request.instructorPhoto());
        block.setActive(request.active());
        return toResponse(gymBlockRepository.save(block));
    }

    public void removeBlock(Long gymId, Long blockId) {
        findGymOrThrow(gymId);
        GymBlock block = findBlockOrThrow(gymId, blockId);
        gymBlockRepository.delete(block);
    }

    public PlanResponse addPlan(Long gymId, PlanCreateRequest request) {
        findGymOrThrow(gymId);
        GymPlan plan = GymPlan.builder()
                .gymId(gymId)
                .name(request.name())
                .description(request.description())
                .category(request.category())
                .priceClp(request.priceClp())
                .monthlyClasses(request.monthlyClasses())
                .active(true)
                .build();
        return toResponse(gymPlanRepository.save(plan));
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listPlans(Long gymId) {
        findGymOrThrow(gymId);
        return gymPlanRepository.findByGymId(gymId).stream().map(this::toResponse).toList();
    }

    public PlanResponse updatePlan(Long gymId, Long planId, PlanUpdateRequest request) {
        findGymOrThrow(gymId);
        GymPlan plan = findPlanOrThrow(gymId, planId);
        plan.setName(request.name());
        plan.setDescription(request.description());
        plan.setCategory(request.category());
        plan.setPriceClp(request.priceClp());
        plan.setMonthlyClasses(request.monthlyClasses());
        plan.setActive(request.active());
        return toResponse(gymPlanRepository.save(plan));
    }

    public void removePlan(Long gymId, Long planId) {
        findGymOrThrow(gymId);
        GymPlan plan = findPlanOrThrow(gymId, planId);
        gymPlanRepository.delete(plan);
    }

    /** Vista del socio: solo planes activos, sin campos internos (gymId, active). */
    @Transactional(readOnly = true)
    public List<MemberPlanResponse> listActivePlans(Long gymId) {
        findGymOrThrow(gymId);
        return gymPlanRepository.findByGymId(gymId).stream()
                .filter(GymPlan::isActive)
                .map(this::toMemberResponse)
                .toList();
    }

    /** Misma vista pero para la página pública de alta (mygym.cl/j/{slug}) — sin auth, resuelta por slug. */
    @Transactional(readOnly = true)
    public List<MemberPlanResponse> listPublicPlansBySlug(String slug) {
        Gym gym = gymRepository.findBySlug(slug).orElseThrow(() -> new GymNotFoundException(slug));
        if (!gym.isActive()) {
            throw new GymNotFoundException(slug);
        }
        return gymPlanRepository.findByGymId(gym.getId()).stream()
                .filter(GymPlan::isActive)
                .map(this::toMemberResponse)
                .toList();
    }

    /**
     * Registro MANUAL de pago (efectivo/transferencia fuera del sistema) —
     * el admin marca "pagó" a mano. Desde que existe FlowPaymentService,
     * este es el camino paralelo para dinero que no pasó por Flow; el propio
     * socio ya no puede auto-marcarse como pagado (ver
     * FlowPaymentService.handleWebhook, que es quien persiste
     * plan_id/paid_at reales cuando el pago SÍ pasó por Flow).
     */
    public void simulatePlanPayment(Long gymId, Long memberId, Long planId) {
        Gym gym = findGymOrThrow(gymId);
        GymPlan plan = findPlanOrThrow(gymId, planId);
        AppUser member = appUserRepository
                .findByIdAndGymId(memberId, gymId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
        member.setPlanId(planId);
        member.setPaidAt(Instant.now());
        // Renovación real — cualquier "ya venía usando N clases" que traía de una importación
        // por Excel quedó atrás con el período anterior, no se arrastra (ver AppUser.usedSessionsAtImport).
        member.setUsedSessionsAtImport(null);
        appUserRepository.save(member);
        List<String> adminEmails = appUserRepository.findByGymIdAndRole(gymId, Role.GYM_ADMIN).stream()
                .map(AppUser::getEmail)
                .toList();
        memberLifecycleEmailService.sendPaymentConfirmedMember(gym, member, plan);
        memberLifecycleEmailService.sendPaymentConfirmedAdmin(gym, member, plan, adminEmails);
    }

    /**
     * Contraparte de simulatePlanPayment — le quita a un socio el plan/pago que tenga
     * registrado (plan_id y paid_at a null), sin importar si ese pago vino de Flow o fue
     * marcado a mano. Camino manual para corregir un pago mal confirmado (ej. Flow lo marcó
     * como aprobado pero en realidad falló o se reembolsó) o para dar de baja a un socio por
     * cualquier otro motivo del gimnasio.
     *
     * Dar de baja el plan equivale a expulsar al socio: sus reservas BOOKED a futuro se
     * cancelan acá mismo, liberando el cupo para otros socios. Si más adelante paga de
     * nuevo, arranca desde cero (sin reservas viejas coladas en el ciclo nuevo — bug real
     * reportado: quedaban contando contra el cupo del ciclo recién pagado). Las reservas
     * pasadas no se tocan, quedan como historial de la cuenta.
     */
    public void revokePlan(Long gymId, Long memberId) {
        findGymOrThrow(gymId);
        AppUser member = appUserRepository
                .findByIdAndGymId(memberId, gymId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
        member.setPlanId(null);
        member.setPaidAt(null);
        member.setUsedSessionsAtImport(null);
        appUserRepository.save(member);

        LocalDate today = LocalDate.now(GYM_ZONE);
        List<Reservation> futureReservations = reservationRepository
                .findByMemberIdAndStatusAndClassDateGreaterThanEqual(memberId, ReservationStatus.BOOKED, today);
        futureReservations.forEach(r -> r.setStatus(ReservationStatus.CANCELLED));
        reservationRepository.saveAll(futureReservations);
    }

    private void validateSchedule(LocalTime startTime, LocalTime endTime) {
        if (!startTime.isBefore(endTime)) {
            throw new InvalidBlockScheduleException("La hora de inicio debe ser anterior a la hora de término");
        }
    }

    private Gym findGymOrThrow(Long id) {
        return gymRepository.findById(id).orElseThrow(() -> new GymNotFoundException(id));
    }

    private GymBlock findBlockOrThrow(Long gymId, Long blockId) {
        return gymBlockRepository
                .findByIdAndGymId(blockId, gymId)
                .orElseThrow(() -> new GymBlockNotFoundException(gymId, blockId));
    }

    private GymPlan findPlanOrThrow(Long gymId, Long planId) {
        return gymPlanRepository
                .findByIdAndGymId(planId, gymId)
                .orElseThrow(() -> new GymPlanNotFoundException(gymId, planId));
    }

    private AppUser findAdminOrThrow(Long gymId, Long userId) {
        AppUser admin = appUserRepository
                .findByIdAndGymId(userId, gymId)
                .orElseThrow(() -> new AdminNotFoundException(gymId, userId));
        // Reusado por updateAdminStatus tanto para GYM_ADMIN real como para DEMO_ADMIN (activar/
        // desactivar acceso a la demo comercial) — ambos son "administradores" de este gym en
        // sentido amplio, la diferencia de permisos la hace SecurityConfig, no esta validación.
        if (admin.getRole() != Role.GYM_ADMIN && admin.getRole() != Role.DEMO_ADMIN) {
            throw new AdminNotFoundException(gymId, userId);
        }
        return admin;
    }

    private GymResponse toResponse(Gym gym) {
        return new GymResponse(
                gym.getId(),
                gym.getPublicId().toString(),
                gym.getName(),
                gym.getSlug(),
                gym.isActive(),
                gym.getMaxUsers(),
                gym.isGoogleLoginEnabled(),
                gym.getThemeColor(),
                gym.getThemeMode(),
                gym.getLogoSvg(),
                gym.getTagline(),
                gym.getDescription(),
                gym.getInstagramUrl(),
                gym.getWhatsappNumber(),
                gym.getCancellationWindowHours(),
                gym.getBankName(),
                gym.getBankAccountType(),
                gym.getBankAccountNumber(),
                gym.getBankHolderRut(),
                gym.getBankHolderName(),
                gym.getBankConfirmationEmail(),
                gym.getCreatedAt(),
                gym.getUpdatedAt());
    }

    private AdminResponse toResponse(AppUser admin) {
        return toResponse(admin, Map.of());
    }

    /** progressByAdmin: ver listDemoAdmins — vacío para un admin real (GYM_ADMIN), que nunca
     *  tiene fila en demo_tour_progress. */
    private AdminResponse toResponse(AppUser admin, Map<Long, List<DemoTourProgress>> progressByAdmin) {
        List<DemoTourProgress> progress = progressByAdmin.getOrDefault(admin.getId(), List.of());
        Integer adminTourStep = progress.stream()
                .filter(p -> "ADMIN".equals(p.getTour()))
                .map(DemoTourProgress::getMaxStep)
                .findFirst()
                .orElse(null);
        Integer memberTourStep = progress.stream()
                .filter(p -> "MEMBER".equals(p.getTour()))
                .map(DemoTourProgress::getMaxStep)
                .findFirst()
                .orElse(null);
        return new AdminResponse(
                admin.getId(),
                admin.getName(),
                admin.getEmail(),
                admin.isActive(),
                admin.getPhotoUrl(),
                admin.getCreatedAt(),
                admin.getLastLoginAt(),
                adminTourStep,
                memberTourStep);
    }

    private BlockResponse toResponse(GymBlock block) {
        return new BlockResponse(
                block.getId(),
                block.getGymId(),
                block.getLabel(),
                block.getDayOfWeek(),
                block.getStartTime(),
                block.getEndTime(),
                block.getCapacity(),
                block.getCategory(),
                block.getInstructorName(),
                block.getInstructorPhoto(),
                block.isActive());
    }

    private PlanResponse toResponse(GymPlan plan) {
        return new PlanResponse(
                plan.getId(),
                plan.getGymId(),
                plan.getName(),
                plan.getDescription(),
                plan.getCategory(),
                plan.getPriceClp(),
                plan.getMonthlyClasses(),
                plan.isActive());
    }

    private MemberPlanResponse toMemberResponse(GymPlan plan) {
        return new MemberPlanResponse(
                plan.getId(), plan.getName(), plan.getDescription(), plan.getCategory(), plan.getPriceClp(), plan.getMonthlyClasses());
    }
}
