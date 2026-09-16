package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.dto.AdminCreateRequest;
import com.cortesdev.mygym.models.dto.AdminResponse;
import com.cortesdev.mygym.models.dto.AdminStatusUpdateRequest;
import com.cortesdev.mygym.models.dto.AttendeeResponse;
import com.cortesdev.mygym.models.dto.BlockCreateRequest;
import com.cortesdev.mygym.models.dto.BlockResponse;
import com.cortesdev.mygym.models.dto.BlockUpdateRequest;
import com.cortesdev.mygym.models.dto.BrandingSuggestionRequest;
import com.cortesdev.mygym.models.dto.BrandingSuggestionResponse;
import com.cortesdev.mygym.models.dto.GymConfigUpdateRequest;
import com.cortesdev.mygym.models.dto.GymCreateRequest;
import com.cortesdev.mygym.models.dto.GymIdentityUpdateRequest;
import com.cortesdev.mygym.models.dto.GymPhotoCreateRequest;
import com.cortesdev.mygym.models.dto.GymPhotoResponse;
import com.cortesdev.mygym.models.dto.GymResponse;
import com.cortesdev.mygym.models.dto.MarkPaidRequest;
import com.cortesdev.mygym.models.dto.MemberCreateRequest;
import com.cortesdev.mygym.models.dto.MemberResponse;
import com.cortesdev.mygym.models.dto.PlanCreateRequest;
import com.cortesdev.mygym.models.dto.PlanResponse;
import com.cortesdev.mygym.models.dto.PlanUpdateRequest;
import com.cortesdev.mygym.models.dto.ThemeUpdateRequest;
import com.cortesdev.mygym.services.BrandingSuggestionService;
import com.cortesdev.mygym.services.GymService;
import com.cortesdev.mygym.services.MemberService;
import com.cortesdev.mygym.services.ReservationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gyms")
@RequiredArgsConstructor
public class GymController {

    private final GymService gymService;
    private final MemberService memberService;
    private final BrandingSuggestionService brandingSuggestionService;
    private final ReservationService reservationService;

    @PostMapping("/suggest-branding")
    public BrandingSuggestionResponse suggestBranding(@Valid @RequestBody BrandingSuggestionRequest request) {
        return brandingSuggestionService.suggest(request.name());
    }

    @PostMapping
    public ResponseEntity<GymResponse> createGym(@Valid @RequestBody GymCreateRequest request) {
        GymResponse gym = gymService.createGym(request);
        return ResponseEntity.created(URI.create("/api/gyms/" + gym.id())).body(gym);
    }

    @GetMapping
    public List<GymResponse> listGyms(@RequestParam(required = false) Boolean active) {
        return gymService.listGyms(active);
    }

    @GetMapping("/{id}")
    public GymResponse getGym(@PathVariable Long id) {
        return gymService.getGym(id);
    }

    /** Resuelve el UUID opaco de la URL del super-admin al gym completo (nunca se expone el id secuencial en rutas). */
    @GetMapping("/by-public-id/{publicId}")
    public GymResponse getGymByPublicId(@PathVariable java.util.UUID publicId) {
        return gymService.getGymByPublicId(publicId);
    }

    @PutMapping("/{id}/config")
    public GymResponse updateGymConfig(@PathVariable Long id, @Valid @RequestBody GymConfigUpdateRequest request) {
        return gymService.updateGymConfig(id, request);
    }

    @PostMapping("/{id}/blocks")
    public ResponseEntity<BlockResponse> addBlock(
            @PathVariable Long id, @Valid @RequestBody BlockCreateRequest request) {
        BlockResponse block = gymService.addBlock(id, request);
        return ResponseEntity.created(URI.create("/api/gyms/" + id + "/blocks/" + block.id()))
                .body(block);
    }

    @GetMapping("/{id}/blocks")
    public List<BlockResponse> listBlocks(@PathVariable Long id) {
        return gymService.listBlocks(id);
    }

    @PutMapping("/{id}/blocks/{blockId}")
    public BlockResponse updateBlock(
            @PathVariable Long id, @PathVariable Long blockId, @Valid @RequestBody BlockUpdateRequest request) {
        return gymService.updateBlock(id, blockId, request);
    }

    @GetMapping("/{id}/blocks/{blockId}/occurrences/{classDate}/attendees")
    public List<AttendeeResponse> blockAttendees(
            @PathVariable Long id,
            @PathVariable Long blockId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate classDate) {
        return reservationService.getOccurrenceAttendees(id, blockId, classDate).stream()
                .map(this::toAttendeeResponse)
                .toList();
    }

    private AttendeeResponse toAttendeeResponse(AppUser user) {
        return new AttendeeResponse(user.getId(), user.getName(), user.getEmail(), user.getPhotoUrl());
    }

    @DeleteMapping("/{id}/blocks/{blockId}")
    public ResponseEntity<Void> removeBlock(@PathVariable Long id, @PathVariable Long blockId) {
        gymService.removeBlock(id, blockId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/admins")
    public List<AdminResponse> listAdmins(@PathVariable Long id) {
        return gymService.listAdmins(id);
    }

    @PostMapping("/{id}/admins")
    public ResponseEntity<AdminResponse> addAdmin(
            @PathVariable Long id, @Valid @RequestBody AdminCreateRequest request) {
        AdminResponse admin = gymService.addAdmin(id, request);
        return ResponseEntity.created(URI.create("/api/gyms/" + id + "/admins/" + admin.id()))
                .body(admin);
    }

    @PutMapping("/{id}/admins/{userId}")
    public AdminResponse updateAdminStatus(
            @PathVariable Long id, @PathVariable Long userId, @Valid @RequestBody AdminStatusUpdateRequest request) {
        return gymService.updateAdminStatus(id, userId, request);
    }

    @GetMapping("/{id}/plans")
    public List<PlanResponse> listPlans(@PathVariable Long id) {
        return gymService.listPlans(id);
    }

    @PostMapping("/{id}/plans")
    public ResponseEntity<PlanResponse> addPlan(@PathVariable Long id, @Valid @RequestBody PlanCreateRequest request) {
        PlanResponse plan = gymService.addPlan(id, request);
        return ResponseEntity.created(URI.create("/api/gyms/" + id + "/plans/" + plan.id())).body(plan);
    }

    @PutMapping("/{id}/plans/{planId}")
    public PlanResponse updatePlan(
            @PathVariable Long id, @PathVariable Long planId, @Valid @RequestBody PlanUpdateRequest request) {
        return gymService.updatePlan(id, planId, request);
    }

    @DeleteMapping("/{id}/plans/{planId}")
    public ResponseEntity<Void> removePlan(@PathVariable Long id, @PathVariable Long planId) {
        gymService.removePlan(id, planId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/theme")
    public GymResponse updateTheme(@PathVariable Long id, @Valid @RequestBody ThemeUpdateRequest request) {
        gymService.updateTheme(id, request.themeColor());
        return gymService.getGym(id);
    }

    @PutMapping("/{id}/identity")
    public GymResponse updateIdentity(@PathVariable Long id, @Valid @RequestBody GymIdentityUpdateRequest request) {
        gymService.updateIdentity(id, request);
        return gymService.getGym(id);
    }

    @GetMapping("/{id}/photos")
    public List<GymPhotoResponse> listPhotos(@PathVariable Long id) {
        return gymService.listPhotos(id);
    }

    @PostMapping("/{id}/photos")
    public ResponseEntity<GymPhotoResponse> addPhoto(
            @PathVariable Long id, @Valid @RequestBody GymPhotoCreateRequest request) {
        GymPhotoResponse photo = gymService.addPhoto(id, request);
        return ResponseEntity.created(URI.create("/api/gyms/" + id + "/photos/" + photo.id())).body(photo);
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    public ResponseEntity<Void> removePhoto(@PathVariable Long id, @PathVariable Long photoId) {
        gymService.removePhoto(id, photoId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/members")
    public List<MemberResponse> listMembers(@PathVariable Long id) {
        return memberService.listMembers(id);
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<MemberResponse> addMember(
            @PathVariable Long id, @Valid @RequestBody MemberCreateRequest request) {
        MemberResponse member = memberService.createMember(id, request);
        return ResponseEntity.created(URI.create("/api/gyms/" + id + "/members/" + member.id())).body(member);
    }

    /** Contraparte SUPER_ADMIN de MemberController.markPaid — mismo backend, gymId por path. */
    @PostMapping("/{id}/members/{memberId}/mark-paid")
    public ResponseEntity<Void> markMemberPaid(
            @PathVariable Long id, @PathVariable Long memberId, @Valid @RequestBody MarkPaidRequest request) {
        gymService.simulatePlanPayment(id, memberId, request.planId());
        return ResponseEntity.noContent().build();
    }
}
