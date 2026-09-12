package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.AdminCreateRequest;
import com.cortesdev.mygym.models.dto.AdminResponse;
import com.cortesdev.mygym.models.dto.AdminStatusUpdateRequest;
import com.cortesdev.mygym.models.dto.BlockCreateRequest;
import com.cortesdev.mygym.models.dto.BlockResponse;
import com.cortesdev.mygym.models.dto.BlockUpdateRequest;
import com.cortesdev.mygym.models.dto.GymConfigUpdateRequest;
import com.cortesdev.mygym.models.dto.GymCreateRequest;
import com.cortesdev.mygym.models.dto.GymResponse;
import com.cortesdev.mygym.models.dto.PublicGymResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.services.exception.AdminNotFoundException;
import com.cortesdev.mygym.services.exception.DuplicateOwnerEmailException;
import com.cortesdev.mygym.services.exception.DuplicateSlugException;
import com.cortesdev.mygym.services.exception.GymBlockNotFoundException;
import com.cortesdev.mygym.services.exception.GymNotFoundException;
import com.cortesdev.mygym.services.exception.InvalidBlockScheduleException;
import com.cortesdev.mygym.services.exception.InvalidLogoException;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class GymService {

    private final GymRepository gymRepository;
    private final GymBlockRepository gymBlockRepository;
    private final AppUserRepository appUserRepository;
    private final AdminInviteEmailService adminInviteEmailService;

    public GymResponse createGym(GymCreateRequest request) {
        if (gymRepository.existsBySlug(request.slug())) {
            throw new DuplicateSlugException(request.slug());
        }
        if (appUserRepository.existsByEmail(request.ownerEmail())) {
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
        adminInviteEmailService.sendAdminInvite(gym, owner);

        return toResponse(gym);
    }

    @Transactional(readOnly = true)
    public List<GymResponse> listGyms(Boolean active) {
        List<Gym> gyms = active == null ? gymRepository.findAll() : gymRepository.findByActive(active);
        return gyms.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public GymResponse getGym(Long id) {
        return toResponse(findGymOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PublicGymResponse getPublicBySlug(String slug) {
        Gym gym = gymRepository.findBySlug(slug).orElseThrow(() -> new GymNotFoundException(slug));
        if (!gym.isActive()) {
            throw new GymNotFoundException(slug);
        }
        String themeColor = gym.getThemeColor() != null ? gym.getThemeColor() : GymPalette.defaultHex();
        return new PublicGymResponse(
                gym.getName(), gym.getSlug(), themeColor, GymPalette.contrastFor(themeColor), gym.getLogoSvg(), gym.isGoogleLoginEnabled());
    }

    public GymResponse updateGymConfig(Long id, GymConfigUpdateRequest request) {
        Gym gym = findGymOrThrow(id);
        gym.setActive(request.active());
        gym.setMaxUsers(request.maxUsers());
        gym.setGoogleLoginEnabled(request.googleLoginEnabled());
        gym.setLogoSvg(request.logoSvg());
        return toResponse(gymRepository.save(gym));
    }

    public void updateTheme(Long gymId, String themeColor) {
        Gym gym = findGymOrThrow(gymId);
        gym.setThemeColor(themeColor);
        gymRepository.save(gym);
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

    @Transactional(readOnly = true)
    public List<AdminResponse> listAdmins(Long gymId) {
        findGymOrThrow(gymId);
        return appUserRepository.findByGymIdAndRole(gymId, Role.GYM_ADMIN).stream()
                .map(this::toResponse)
                .toList();
    }

    public AdminResponse addAdmin(Long gymId, AdminCreateRequest request) {
        Gym gym = findGymOrThrow(gymId);
        if (appUserRepository.existsByEmail(request.email())) {
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
                .active(true)
                .build();
        return toResponse(gymBlockRepository.save(block));
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
        block.setActive(request.active());
        return toResponse(gymBlockRepository.save(block));
    }

    public void removeBlock(Long gymId, Long blockId) {
        findGymOrThrow(gymId);
        GymBlock block = findBlockOrThrow(gymId, blockId);
        gymBlockRepository.delete(block);
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

    private AppUser findAdminOrThrow(Long gymId, Long userId) {
        AppUser admin = appUserRepository
                .findByIdAndGymId(userId, gymId)
                .orElseThrow(() -> new AdminNotFoundException(gymId, userId));
        if (admin.getRole() != Role.GYM_ADMIN) {
            throw new AdminNotFoundException(gymId, userId);
        }
        return admin;
    }

    private GymResponse toResponse(Gym gym) {
        return new GymResponse(
                gym.getId(),
                gym.getName(),
                gym.getSlug(),
                gym.isActive(),
                gym.getMaxUsers(),
                gym.isGoogleLoginEnabled(),
                gym.getThemeColor(),
                gym.getLogoSvg(),
                gym.getCreatedAt(),
                gym.getUpdatedAt());
    }

    private AdminResponse toResponse(AppUser admin) {
        return new AdminResponse(admin.getId(), admin.getName(), admin.getEmail(), admin.isActive());
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
                block.isActive());
    }
}
