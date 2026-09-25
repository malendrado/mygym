package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.dto.TvScreenClaimRequest;
import com.cortesdev.mygym.models.dto.TvScreenResponse;
import com.cortesdev.mygym.security.AuthenticatedUser;
import com.cortesdev.mygym.services.TvScreenService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Scoped al gym del GYM_ADMIN logueado — mismo criterio que GymAdminController, gymId siempre
 *  sale del JWT, nunca de un path variable. */
@RestController
@RequestMapping("/api/gym-admin/tv-screens")
@RequiredArgsConstructor
public class TvAdminController {

    private final TvScreenService tvScreenService;

    @GetMapping
    public List<TvScreenResponse> listMyScreens(@AuthenticationPrincipal Jwt jwt) {
        return tvScreenService.listScreens(AuthenticatedUser.from(jwt).gymId());
    }

    @PostMapping
    public ResponseEntity<TvScreenResponse> claimScreen(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TvScreenClaimRequest request) {
        Long gymId = AuthenticatedUser.from(jwt).gymId();
        TvScreenResponse screen = tvScreenService.claimPairingCode(gymId, request.code(), request.name());
        return ResponseEntity.created(URI.create("/api/gym-admin/tv-screens/" + screen.id())).body(screen);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeMyScreen(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        tvScreenService.deleteScreen(AuthenticatedUser.from(jwt).gymId(), id);
        return ResponseEntity.noContent().build();
    }
}
