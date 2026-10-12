package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.dto.MemberProfileRequest;
import com.cortesdev.mygym.models.dto.MemberResponse;
import com.cortesdev.mygym.security.AuthenticatedUser;
import com.cortesdev.mygym.services.MemberProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Datos de contacto del socio. El propio socio (/api/me, solo MEMBER por SecurityConfig) y el
 *  admin del gym (/api/gym-admin, GYM_ADMIN y PROFESOR, ver SecurityConfig). La
 *  contraparte SUPER_ADMIN vive en GymController. El gym SIEMPRE sale del JWT. */
@RestController
@RequiredArgsConstructor
public class MemberProfileController {

    private final MemberProfileService memberProfileService;

    @PutMapping("/api/me/profile")
    public MemberResponse updateOwnProfile(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MemberProfileRequest request) {
        return memberProfileService.updateOwn(AuthenticatedUser.from(jwt).userId(), request);
    }

    @PutMapping("/api/gym-admin/members/{memberId}/profile")
    public MemberResponse updateMemberProfile(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long memberId, @Valid @RequestBody MemberProfileRequest request) {
        return memberProfileService.updateForGym(AuthenticatedUser.from(jwt).gymId(), memberId, request);
    }
}
