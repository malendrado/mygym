package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.dto.AttendeeResponse;
import com.cortesdev.mygym.models.dto.BlockCreateRequest;
import com.cortesdev.mygym.models.dto.BlockOccurrenceAttendeesResponse;
import com.cortesdev.mygym.models.dto.OccurrenceAttendees;
import com.cortesdev.mygym.models.dto.BlockResponse;
import com.cortesdev.mygym.models.dto.BlockUpdateRequest;
import com.cortesdev.mygym.models.dto.GymIdentityUpdateRequest;
import com.cortesdev.mygym.models.dto.GymLogoUpdateRequest;
import com.cortesdev.mygym.models.dto.GymPhotoCreateRequest;
import com.cortesdev.mygym.models.dto.GymPhotoResponse;
import com.cortesdev.mygym.models.dto.GymResponse;
import com.cortesdev.mygym.models.dto.PlanCreateRequest;
import com.cortesdev.mygym.models.dto.PlanResponse;
import com.cortesdev.mygym.models.dto.PlanUpdateRequest;
import com.cortesdev.mygym.models.dto.ThemeUpdateRequest;
import com.cortesdev.mygym.security.AuthenticatedUser;
import com.cortesdev.mygym.services.GymService;
import com.cortesdev.mygym.services.ReservationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Scoped for the logged-in gym owner (GYM_ADMIN): gymId always comes from the
 * caller's own JWT, never from a client-supplied path variable, so a gym
 * admin can never address another gym's data. Delegates to the same
 * GymService used by the SUPER_ADMIN-only GymController.
 */
@RestController
@RequestMapping("/api/gym-admin/gym")
@RequiredArgsConstructor
public class GymAdminController {

    private final GymService gymService;
    private final ReservationService reservationService;

    @GetMapping
    public GymResponse getMyGym(@AuthenticationPrincipal Jwt jwt) {
        return gymService.getGym(AuthenticatedUser.from(jwt).gymId());
    }

    @GetMapping("/blocks/{blockId}/occurrences/{classDate}/attendees")
    public List<AttendeeResponse> myBlockAttendees(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long blockId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate classDate) {
        Long gymId = AuthenticatedUser.from(jwt).gymId();
        return reservationService.getOccurrenceAttendees(gymId, blockId, classDate).stream()
                .map(this::toAttendeeResponse)
                .toList();
    }

    private AttendeeResponse toAttendeeResponse(AppUser user) {
        return new AttendeeResponse(user.getId(), user.getName(), user.getEmail(), user.getPhotoUrl());
    }

    // Batch para la pestaña Historial — una sola llamada por semana en vez de una por cada
    // bloque×día (ver ReservationService.getOccurrenceAttendeesForRange, arregla la lentitud
    // reportada por fan-out de requests contra el pool de conexiones).
    @GetMapping("/history-attendees")
    public List<BlockOccurrenceAttendeesResponse> myHistoryAttendees(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Long gymId = AuthenticatedUser.from(jwt).gymId();
        return reservationService.getOccurrenceAttendeesForRange(gymId, from, to).stream()
                .map(this::toBatchResponse)
                .toList();
    }

    private BlockOccurrenceAttendeesResponse toBatchResponse(OccurrenceAttendees occurrence) {
        return new BlockOccurrenceAttendeesResponse(
                occurrence.gymBlockId(),
                occurrence.classDate(),
                occurrence.attendees().stream().map(this::toAttendeeResponse).toList());
    }

    @GetMapping("/blocks")
    public List<BlockResponse> listMyBlocks(@AuthenticationPrincipal Jwt jwt) {
        return gymService.listBlocks(AuthenticatedUser.from(jwt).gymId());
    }

    @PostMapping("/blocks")
    public ResponseEntity<BlockResponse> addMyBlock(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BlockCreateRequest request) {
        Long gymId = AuthenticatedUser.from(jwt).gymId();
        BlockResponse block = gymService.addBlock(gymId, request);
        return ResponseEntity.created(URI.create("/api/gym-admin/gym/blocks/" + block.id())).body(block);
    }

    @PutMapping("/blocks/{blockId}")
    public BlockResponse updateMyBlock(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long blockId, @Valid @RequestBody BlockUpdateRequest request) {
        return gymService.updateBlock(AuthenticatedUser.from(jwt).gymId(), blockId, request);
    }

    @DeleteMapping("/blocks/{blockId}")
    public ResponseEntity<Void> removeMyBlock(@AuthenticationPrincipal Jwt jwt, @PathVariable Long blockId) {
        gymService.removeBlock(AuthenticatedUser.from(jwt).gymId(), blockId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/plans")
    public List<PlanResponse> listMyPlans(@AuthenticationPrincipal Jwt jwt) {
        return gymService.listPlans(AuthenticatedUser.from(jwt).gymId());
    }

    @PostMapping("/plans")
    public ResponseEntity<PlanResponse> addMyPlan(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PlanCreateRequest request) {
        Long gymId = AuthenticatedUser.from(jwt).gymId();
        PlanResponse plan = gymService.addPlan(gymId, request);
        return ResponseEntity.created(URI.create("/api/gym-admin/gym/plans/" + plan.id())).body(plan);
    }

    @PutMapping("/plans/{planId}")
    public PlanResponse updateMyPlan(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long planId, @Valid @RequestBody PlanUpdateRequest request) {
        return gymService.updatePlan(AuthenticatedUser.from(jwt).gymId(), planId, request);
    }

    @DeleteMapping("/plans/{planId}")
    public ResponseEntity<Void> removeMyPlan(@AuthenticationPrincipal Jwt jwt, @PathVariable Long planId) {
        gymService.removePlan(AuthenticatedUser.from(jwt).gymId(), planId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/theme")
    public GymResponse updateMyTheme(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ThemeUpdateRequest request) {
        Long gymId = AuthenticatedUser.from(jwt).gymId();
        gymService.updateTheme(gymId, request.themeColor());
        return gymService.getGym(gymId);
    }

    @PutMapping("/logo")
    public GymResponse updateMyLogo(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody GymLogoUpdateRequest request) {
        Long gymId = AuthenticatedUser.from(jwt).gymId();
        gymService.updateMyLogo(gymId, request.logo());
        return gymService.getGym(gymId);
    }

    @PutMapping("/identity")
    public GymResponse updateMyIdentity(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody GymIdentityUpdateRequest request) {
        Long gymId = AuthenticatedUser.from(jwt).gymId();
        gymService.updateIdentity(gymId, request);
        return gymService.getGym(gymId);
    }

    @GetMapping("/photos")
    public List<GymPhotoResponse> listMyPhotos(@AuthenticationPrincipal Jwt jwt) {
        return gymService.listPhotos(AuthenticatedUser.from(jwt).gymId());
    }

    @PostMapping("/photos")
    public ResponseEntity<GymPhotoResponse> addMyPhoto(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody GymPhotoCreateRequest request) {
        Long gymId = AuthenticatedUser.from(jwt).gymId();
        GymPhotoResponse photo = gymService.addPhoto(gymId, request);
        return ResponseEntity.created(URI.create("/api/gym-admin/gym/photos/" + photo.id())).body(photo);
    }

    @DeleteMapping("/photos/{photoId}")
    public ResponseEntity<Void> removeMyPhoto(@AuthenticationPrincipal Jwt jwt, @PathVariable Long photoId) {
        gymService.removePhoto(AuthenticatedUser.from(jwt).gymId(), photoId);
        return ResponseEntity.noContent().build();
    }
}
