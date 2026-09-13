package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.dto.GoogleLoginRequest;
import com.cortesdev.mygym.models.dto.GymPhotoResponse;
import com.cortesdev.mygym.models.dto.LoginResponse;
import com.cortesdev.mygym.models.dto.MemberPlanResponse;
import com.cortesdev.mygym.models.dto.PublicGymResponse;
import com.cortesdev.mygym.services.AuthService;
import com.cortesdev.mygym.services.GymService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fully public (no auth) — powers the per-gym self-signup page at
 * mygym.cl/j/{slug}. Never exposes anything beyond a gym's own public
 * branding, and joinGym only ever provisions a MEMBER of the one gym named
 * by the slug (see AuthService.joinGymWithGoogle for why that's safe).
 */
@RestController
@RequestMapping("/api/public/gyms")
@RequiredArgsConstructor
public class PublicGymController {

    private final GymService gymService;
    private final AuthService authService;

    @GetMapping("/{slug}")
    public PublicGymResponse getPublicGym(@PathVariable String slug) {
        return gymService.getPublicBySlug(slug);
    }

    @GetMapping("/{slug}/plans")
    public List<MemberPlanResponse> getPublicPlans(@PathVariable String slug) {
        return gymService.listPublicPlansBySlug(slug);
    }

    @GetMapping("/{slug}/photos")
    public List<GymPhotoResponse> getPublicPhotos(@PathVariable String slug) {
        return gymService.listPublicPhotosBySlug(slug);
    }

    @PostMapping("/{slug}/join")
    public LoginResponse joinGym(@PathVariable String slug, @Valid @RequestBody GoogleLoginRequest request) {
        return authService.joinGymWithGoogle(request.idToken(), slug);
    }
}
