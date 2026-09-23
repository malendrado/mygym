package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.dto.MarkPaidRequest;
import com.cortesdev.mygym.models.dto.MemberCreateRequest;
import com.cortesdev.mygym.models.dto.MemberImportRequest;
import com.cortesdev.mygym.models.dto.MemberImportRowResult;
import com.cortesdev.mygym.models.dto.MemberResponse;
import com.cortesdev.mygym.security.AuthenticatedUser;
import com.cortesdev.mygym.services.GymService;
import com.cortesdev.mygym.services.MemberService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gym-admin/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final GymService gymService;

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

    /** Carga masiva desde Excel — ver MemberService.importMembers para el detalle de qué pasa por
     *  fila. El frontend manda esto en bloques de ~20 filas, nunca el archivo entero de una vez. */
    @PostMapping("/import")
    public List<MemberImportRowResult> importMembers(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MemberImportRequest request) {
        return memberService.importMembers(AuthenticatedUser.from(jwt).gymId(), request.rows());
    }

    /** El admin registra a mano que un socio pagó — ver GymService.simulatePlanPayment. */
    @PostMapping("/{memberId}/mark-paid")
    public ResponseEntity<Void> markPaid(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long memberId, @Valid @RequestBody MarkPaidRequest request) {
        gymService.simulatePlanPayment(AuthenticatedUser.from(jwt).gymId(), memberId, request.planId());
        return ResponseEntity.noContent().build();
    }

    /** El admin le quita a un socio el plan/pago que tenga registrado — ver GymService.revokePlan. */
    @PostMapping("/{memberId}/revoke-plan")
    public ResponseEntity<Void> revokePlan(@AuthenticationPrincipal Jwt jwt, @PathVariable Long memberId) {
        gymService.revokePlan(AuthenticatedUser.from(jwt).gymId(), memberId);
        return ResponseEntity.noContent().build();
    }
}
