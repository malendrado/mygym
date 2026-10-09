package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.dto.AttendeeSummaryResponse;
import com.cortesdev.mygym.models.dto.BankTransferInfoResponse;
import com.cortesdev.mygym.models.dto.CheckinRequest;
import com.cortesdev.mygym.models.dto.CheckinResponse;
import com.cortesdev.mygym.models.dto.GymBlockOccurrenceResponse;
import com.cortesdev.mygym.models.dto.GymClosureNoticeResponse;
import com.cortesdev.mygym.models.dto.GymPhotoResponse;
import com.cortesdev.mygym.models.dto.MemberDashboardResponse;
import com.cortesdev.mygym.models.dto.MemberPlanResponse;
import com.cortesdev.mygym.models.dto.MemberReservation;
import com.cortesdev.mygym.models.dto.MemberResponse;
import com.cortesdev.mygym.models.dto.PageResponse;
import com.cortesdev.mygym.models.dto.PublicGymResponse;
import com.cortesdev.mygym.models.dto.ReservationCreateRequest;
import com.cortesdev.mygym.models.dto.ReservationResponse;
import com.cortesdev.mygym.models.dto.CheckoutResponse;
import com.cortesdev.mygym.security.AuthenticatedUser;
import com.cortesdev.mygym.services.FlowPaymentService;
import com.cortesdev.mygym.services.GymClosureService;
import com.cortesdev.mygym.services.GymService;
import com.cortesdev.mygym.services.MemberService;
import com.cortesdev.mygym.services.ReservationService;
import com.cortesdev.mygym.services.WaitlistService;
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
    private final FlowPaymentService flowPaymentService;
    private final WaitlistService waitlistService;
    private final GymClosureService gymClosureService;

    @GetMapping("/gym")
    public PublicGymResponse myGym(@AuthenticationPrincipal Jwt jwt) {
        return gymService.getPublicById(AuthenticatedUser.from(jwt).gymId());
    }

    // Combina gym+membresía+planes+datos bancarios+aviso de cierre en 1 sola request — antes eran
    // 5 llamadas sueltas disparadas en paralelo desde member.ts, que sumadas a fotos/ocurrencias/
    // mis-reservas llegaban a 7-8 conexiones simultáneas contra un pool Hikari de solo 5 (auditoría
    // de performance 2026-10-09). Deja afuera a propósito fotos/ocurrencias/reservas: son las
    // llamadas más pesadas y las que menos urge pintar primero.
    @GetMapping("/dashboard")
    public MemberDashboardResponse myDashboard(@AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        return new MemberDashboardResponse(
                gymService.getPublicById(user.gymId()),
                memberService.getOwnMembership(user.userId()),
                gymService.listActivePlans(user.gymId()),
                gymService.getBankTransferInfo(user.gymId()),
                gymClosureService.activeOrUpcomingNotice(user.gymId()));
    }

    // Banner de cierre de emergencia (ver GymClosureService) — 204 si no hay ninguno vigente ni próximo.
    @GetMapping("/gym/closure-notice")
    public ResponseEntity<GymClosureNoticeResponse> myClosureNotice(@AuthenticationPrincipal Jwt jwt) {
        GymClosureNoticeResponse notice = gymClosureService.activeOrUpcomingNotice(AuthenticatedUser.from(jwt).gymId());
        return notice == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(notice);
    }

    @GetMapping("/membership")
    public MemberResponse myMembership(@AuthenticationPrincipal Jwt jwt) {
        return memberService.getOwnMembership(AuthenticatedUser.from(jwt).userId());
    }

    @GetMapping("/plans")
    public List<MemberPlanResponse> myPlans(@AuthenticationPrincipal Jwt jwt) {
        return gymService.listActivePlans(AuthenticatedUser.from(jwt).gymId());
    }

    // Datos bancarios de SU gym para pagar por transferencia (alternativa a Flow) —
    // nunca expuestos en la página pública de alta, solo acá, al socio autenticado.
    @GetMapping("/gym/bank-transfer")
    public BankTransferInfoResponse myGymBankTransfer(@AuthenticationPrincipal Jwt jwt) {
        return gymService.getBankTransferInfo(AuthenticatedUser.from(jwt).gymId());
    }

    // Arranca el pago real con Flow.cl (manual, un mes por vez — reemplaza al
    // viejo simulate-payment). El frontend redirige el navegador completo a
    // la URL devuelta; la confirmación real llega después por el webhook
    // público de Flow, nunca acá (nunca confiar en que "empezar el checkout"
    // significa que ya pagó).
    @PostMapping("/plans/{planId}/checkout")
    public CheckoutResponse startCheckout(@AuthenticationPrincipal Jwt jwt, @PathVariable Long planId) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        String redirectUrl = flowPaymentService.startCheckout(user.gymId(), user.userId(), planId);
        return new CheckoutResponse(redirectUrl);
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

    private AttendeeSummaryResponse toAttendeeSummary(MemberReservation mr) {
        String name = mr.member().getName();
        String firstName = name == null ? "Socio" : name.split(" ")[0];
        return new AttendeeSummaryResponse(firstName, mr.member().getPhotoUrl());
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

    // Historial paginado ("Mis reservas · Pasadas") — ver ReservationService.myPastReservations.
    @GetMapping("/reservations/past")
    public PageResponse<ReservationResponse> myPastReservations(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return reservationService.myPastReservations(AuthenticatedUser.from(jwt).userId(), page, size);
    }

    // Lista de espera de una clase llena — ver WaitlistService. Reusa ReservationCreateRequest
    // (mismos dos campos: gymBlockId + classDate) en vez de crear un DTO idéntico aparte.
    @PostMapping("/waitlist")
    public ResponseEntity<Void> joinWaitlist(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReservationCreateRequest request) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        waitlistService.join(user.gymId(), user.userId(), request.gymBlockId(), request.classDate());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/waitlist")
    public ResponseEntity<Void> leaveWaitlist(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam Long gymBlockId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate classDate) {
        waitlistService.leave(AuthenticatedUser.from(jwt).userId(), gymBlockId, classDate);
        return ResponseEntity.noContent().build();
    }

    // Canjea el QR rotativo que muestra la TV del gym durante una clase en curso — confirma
    // asistencia real a la reserva que ya tenía. Ver ReservationService.checkIn.
    @PostMapping("/checkin")
    public CheckinResponse checkIn(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CheckinRequest request) {
        List<String> classLabels =
                reservationService.checkIn(AuthenticatedUser.from(jwt).userId(), request.code().trim().toUpperCase());
        return new CheckinResponse(classLabels);
    }
}
