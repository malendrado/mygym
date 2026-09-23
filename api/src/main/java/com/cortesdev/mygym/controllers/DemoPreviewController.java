package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.AttendeeSummaryResponse;
import com.cortesdev.mygym.models.dto.BankTransferInfoResponse;
import com.cortesdev.mygym.models.dto.GymBlockOccurrenceResponse;
import com.cortesdev.mygym.models.dto.GymPhotoResponse;
import com.cortesdev.mygym.models.dto.MemberPlanResponse;
import com.cortesdev.mygym.models.dto.MemberResponse;
import com.cortesdev.mygym.models.dto.PublicGymResponse;
import com.cortesdev.mygym.models.dto.ReservationResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.security.AuthenticatedUser;
import com.cortesdev.mygym.services.GymService;
import com.cortesdev.mygym.services.MemberService;
import com.cortesdev.mygym.services.ReservationService;
import com.cortesdev.mygym.services.exception.DemoSampleMemberMissingException;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Ver como socio" para el toggle de la demo comercial (Role.DEMO_ADMIN, ver SecurityConfig).
 * Espejo de solo-lectura de ReservationController (/api/me/**), pero en vez de mostrar los datos
 * de QUIEN llama (un DEMO_ADMIN no es socio, no tiene reservas propias), muestra los datos de un
 * socio de muestra fijo — el único MEMBER sembrado en el gym demo — igual para cualquiera que
 * mire la demo. Nunca expone endpoints de escritura: reservar/cancelar quedan deshabilitados en
 * el frontend para esta vista, y aunque alguien pegara directo a /api/me/reservations con este
 * JWT, DEMO_ADMIN no tiene el rol MEMBER que exige ese prefijo en SecurityConfig.
 */
@RestController
@RequestMapping("/api/gym-admin/demo-preview")
@RequiredArgsConstructor
public class DemoPreviewController {

    private final AppUserRepository appUserRepository;
    private final GymService gymService;
    private final MemberService memberService;
    private final ReservationService reservationService;

    @GetMapping("/gym")
    public PublicGymResponse gym(@AuthenticationPrincipal Jwt jwt) {
        return gymService.getPublicById(gymId(jwt));
    }

    @GetMapping("/membership")
    public MemberResponse membership(@AuthenticationPrincipal Jwt jwt) {
        return memberService.getOwnMembership(sampleMemberId(jwt));
    }

    @GetMapping("/plans")
    public List<MemberPlanResponse> plans(@AuthenticationPrincipal Jwt jwt) {
        return gymService.listActivePlans(gymId(jwt));
    }

    @GetMapping("/gym/photos")
    public List<GymPhotoResponse> photos(@AuthenticationPrincipal Jwt jwt) {
        return gymService.listPhotos(gymId(jwt));
    }

    @GetMapping("/gym/bank-transfer")
    public BankTransferInfoResponse bankTransfer(@AuthenticationPrincipal Jwt jwt) {
        return gymService.getBankTransferInfo(gymId(jwt));
    }

    // Mismo recorte de privacidad que ReservationController.myBlockAttendees: solo nombre de pila
    // y foto, nunca email — aunque acá sean socios ficticios, la demo tiene que mostrar
    // exactamente lo que vería un socio real, ni más ni menos.
    @GetMapping("/gym-blocks/{blockId}/occurrences/{classDate}/attendees")
    public List<AttendeeSummaryResponse> attendees(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long blockId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate classDate) {
        return reservationService.getOccurrenceAttendees(gymId(jwt), blockId, classDate).stream()
                .map(mr -> {
                    String name = mr.member().getName();
                    String firstName = name == null ? "Socio" : name.split(" ")[0];
                    return new AttendeeSummaryResponse(firstName, mr.member().getPhotoUrl());
                })
                .toList();
    }

    @GetMapping("/gym-blocks")
    public List<GymBlockOccurrenceResponse> occurrences(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Long gymId = gymId(jwt);
        return reservationService.listOccurrences(gymId, sampleMemberId(gymId), from, to);
    }

    @GetMapping("/reservations")
    public List<ReservationResponse> reservations(@AuthenticationPrincipal Jwt jwt) {
        return reservationService.myReservations(sampleMemberId(jwt));
    }

    private Long gymId(Jwt jwt) {
        return AuthenticatedUser.from(jwt).gymId();
    }

    private Long sampleMemberId(Jwt jwt) {
        return sampleMemberId(gymId(jwt));
    }

    private Long sampleMemberId(Long gymId) {
        return appUserRepository.findByGymIdAndRole(gymId, Role.MEMBER).stream()
                .findFirst()
                .map(AppUser::getId)
                .orElseThrow(() -> new DemoSampleMemberMissingException(gymId));
    }
}
