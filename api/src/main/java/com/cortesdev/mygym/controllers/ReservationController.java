package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.dto.AttendeeSummaryResponse;
import com.cortesdev.mygym.models.dto.GymBlockOccurrenceResponse;
import com.cortesdev.mygym.models.dto.GymPhotoResponse;
import com.cortesdev.mygym.models.dto.MemberPlanResponse;
import com.cortesdev.mygym.models.dto.MemberResponse;
import com.cortesdev.mygym.models.dto.PublicGymResponse;
import com.cortesdev.mygym.models.dto.ReservationCreateRequest;
import com.cortesdev.mygym.models.dto.ReservationResponse;
import com.cortesdev.mygym.security.AuthenticatedUser;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final GymService gymService;
    private final MemberService memberService;

    @GetMapping("/gym")
    public PublicGymResponse myGym(@AuthenticationPrincipal Jwt jwt) {
        return gymService.getPublicById(AuthenticatedUser.from(jwt).gymId());
    }

    @GetMapping("/membership")
    public MemberResponse myMembership(@AuthenticationPrincipal Jwt jwt) {
        return memberService.getOwnMembership(AuthenticatedUser.from(jwt).userId());
    }

    @GetMapping("/plans")
    public List<MemberPlanResponse> myPlans(@AuthenticationPrincipal Jwt jwt) {
        return gymService.listActivePlans(AuthenticatedUser.from(jwt).gymId());
    }

    // Simula la confirmación de pago de Flow.cl (Parte B, todavía sin construir)
    // — solo dispara los emails de "pago confirmado" a socio y admin. No crea
    // ninguna suscripción real todavía.
    @PostMapping("/plans/{planId}/simulate-payment")
    public ResponseEntity<Void> simulatePlanPayment(@AuthenticationPrincipal Jwt jwt, @PathVariable Long planId) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        gymService.simulatePlanPayment(user.gymId(), user.userId(), planId);
        return ResponseEntity.noContent().build();
    }

    // Simula que se cumplió el mes desde el último pago (Parte B, Flow.cl,
    // todavía sin construir) — el cliente calcula la fecha de vencimiento y
    // dispara esto una vez; solo manda los emails de aviso a socio y admin.
    @PostMapping("/plans/{planId}/simulate-expiry")
    public ResponseEntity<Void> simulatePlanExpiry(@AuthenticationPrincipal Jwt jwt, @PathVariable Long planId) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        gymService.simulatePlanExpiry(user.gymId(), user.userId(), planId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/gym/photos")
    public List<GymPhotoResponse> myGymPhotos(@AuthenticationPrincipal Jwt jwt) {
        return gymService.listPhotos(AuthenticatedUser.from(jwt).gymId());
    }

    // Vista reducida para OTROS socios: solo nombre de pila + foto, nunca
    // email ni apellido — pedido explícito del usuario ("el email nunca
    // debes mostrarlo"). Reusa el mismo ReservationService que el admin,
    // pero el controller decide acá qué campos exponer, no el servicio.
    @GetMapping("/gym-blocks/{blockId}/occurrences/{classDate}/attendees")
    public List<AttendeeSummaryResponse> myBlockAttendees(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long blockId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate classDate) {
        Long gymId = AuthenticatedUser.from(jwt).gymId();
        return reservationService.getOccurrenceAttendees(gymId, blockId, classDate).stream()
                .map(this::toAttendeeSummary)
                .toList();
    }

    private AttendeeSummaryResponse toAttendeeSummary(AppUser user) {
        String firstName = user.getName() == null ? "Socio" : user.getName().split(" ")[0];
        return new AttendeeSummaryResponse(firstName, user.getPhotoUrl());
    }

    @GetMapping("/gym-blocks")
    public List<GymBlockOccurrenceResponse> listOccurrences(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        return reservationService.listOccurrences(user.gymId(), user.userId(), from, to);
    }

    @PostMapping("/reservations")
    public ResponseEntity<ReservationResponse> book(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReservationCreateRequest request) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        ReservationResponse reservation = reservationService.book(user.gymId(), user.userId(), request);
        return ResponseEntity.created(URI.create("/api/me/reservations/" + reservation.id())).body(reservation);
    }

    @DeleteMapping("/reservations/{id}")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        reservationService.cancel(AuthenticatedUser.from(jwt).userId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/reservations")
    public List<ReservationResponse> myReservations(@AuthenticationPrincipal Jwt jwt) {
        return reservationService.myReservations(AuthenticatedUser.from(jwt).userId());
    }
}
