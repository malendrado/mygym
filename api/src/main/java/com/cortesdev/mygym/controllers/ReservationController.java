package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.dto.GymBlockOccurrenceResponse;
import com.cortesdev.mygym.models.dto.PublicGymResponse;
import com.cortesdev.mygym.models.dto.ReservationCreateRequest;
import com.cortesdev.mygym.models.dto.ReservationResponse;
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

    @GetMapping("/gym")
    public PublicGymResponse myGym(@AuthenticationPrincipal Jwt jwt) {
        return gymService.getPublicById(AuthenticatedUser.from(jwt).gymId());
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
