package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.dto.MemberCreateRequest;
import com.cortesdev.mygym.models.dto.MemberResponse;
import com.cortesdev.mygym.security.AuthenticatedUser;
import com.cortesdev.mygym.services.MemberService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gym-admin/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @PostMapping
    public ResponseEntity<MemberResponse> createMember(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MemberCreateRequest request) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        MemberResponse member = memberService.createMember(user.gymId(), request);
        return ResponseEntity.created(URI.create("/api/gym-admin/members/" + member.id())).body(member);
    }

    @GetMapping
    public List<MemberResponse> listMembers(@AuthenticationPrincipal Jwt jwt) {
        return memberService.listMembers(AuthenticatedUser.from(jwt).gymId());
    }
}
