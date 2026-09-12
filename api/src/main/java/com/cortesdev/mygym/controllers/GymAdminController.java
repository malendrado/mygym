package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.dto.BlockCreateRequest;
import com.cortesdev.mygym.models.dto.BlockResponse;
import com.cortesdev.mygym.models.dto.BlockUpdateRequest;
import com.cortesdev.mygym.models.dto.GymLogoUpdateRequest;
import com.cortesdev.mygym.models.dto.GymResponse;
import com.cortesdev.mygym.models.dto.ThemeUpdateRequest;
import com.cortesdev.mygym.security.AuthenticatedUser;
import com.cortesdev.mygym.services.GymService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
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

    @GetMapping
    public GymResponse getMyGym(@AuthenticationPrincipal Jwt jwt) {
        return gymService.getGym(AuthenticatedUser.from(jwt).gymId());
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
}
