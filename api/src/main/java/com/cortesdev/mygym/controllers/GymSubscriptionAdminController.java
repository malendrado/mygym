package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.dto.GymSubscriptionDtos.ActivateRequest;
import com.cortesdev.mygym.models.dto.GymSubscriptionDtos.BillingInfoRequest;
import com.cortesdev.mygym.models.dto.GymSubscriptionDtos.ChangePlanRequest;
import com.cortesdev.mygym.models.dto.GymSubscriptionDtos.ManualPaymentRequest;
import com.cortesdev.mygym.models.dto.GymSubscriptionDtos.RejectTransferRequest;
import com.cortesdev.mygym.models.dto.GymSubscriptionDtos.SubscriptionResponse;
import com.cortesdev.mygym.models.dto.GymSubscriptionDtos.SubscriptionUpdateRequest;
import com.cortesdev.mygym.security.AuthenticatedUser;
import com.cortesdev.mygym.services.GymSubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Suscripcion de cualquier gimnasio a mygym, para el super-admin (todo /api/gyms/** exige SUPER_ADMIN). */
@RestController
@RequestMapping("/api/gyms/{gymId}/subscription")
@RequiredArgsConstructor
public class GymSubscriptionAdminController {

    private final GymSubscriptionService subscriptionService;

    @GetMapping
    public SubscriptionResponse get(@PathVariable Long gymId) {
        return subscriptionService.get(gymId);
    }

    /** Activa un gimnasio nuevo al recibir su primer pago. */
    @PostMapping("/activate")
    public SubscriptionResponse activate(
            @PathVariable Long gymId, @Valid @RequestBody ActivateRequest request, @AuthenticationPrincipal Jwt jwt) {
        return subscriptionService.activate(gymId, request, AuthenticatedUser.from(jwt).userId());
    }

    @PutMapping
    public SubscriptionResponse update(@PathVariable Long gymId, @Valid @RequestBody SubscriptionUpdateRequest request) {
        return subscriptionService.update(gymId, request);
    }

    /** Cambio de plan: rige desde el proximo periodo. */
    @PutMapping("/plan")
    public SubscriptionResponse changePlan(@PathVariable Long gymId, @Valid @RequestBody ChangePlanRequest request) {
        return subscriptionService.changePlan(gymId, request);
    }

    @PutMapping("/billing")
    public SubscriptionResponse updateBilling(@PathVariable Long gymId, @Valid @RequestBody BillingInfoRequest request) {
        return subscriptionService.updateBilling(gymId, request);
    }

    /** Registra un pago a mano (efectivo, transferencia o Flow fuera del sistema). */
    @PostMapping("/payments")
    public SubscriptionResponse markPaid(
            @PathVariable Long gymId, @Valid @RequestBody ManualPaymentRequest request, @AuthenticationPrincipal Jwt jwt) {
        return subscriptionService.markPaid(gymId, request, AuthenticatedUser.from(jwt).userId());
    }

    @PostMapping("/payments/{paymentId}/confirm")
    public SubscriptionResponse confirm(@PathVariable Long gymId, @PathVariable Long paymentId, @AuthenticationPrincipal Jwt jwt) {
        return subscriptionService.confirmTransfer(gymId, paymentId, AuthenticatedUser.from(jwt).userId());
    }

    @PostMapping("/payments/{paymentId}/reject")
    public SubscriptionResponse reject(
            @PathVariable Long gymId,
            @PathVariable Long paymentId,
            @Valid @RequestBody RejectTransferRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return subscriptionService.rejectTransfer(gymId, paymentId, request.reason(), AuthenticatedUser.from(jwt).userId());
    }

    /** Anula el ultimo pago confirmado (por ejemplo, uno marcado por error). */
    @PostMapping("/payments/{paymentId}/void")
    public SubscriptionResponse voidPayment(@PathVariable Long gymId, @PathVariable Long paymentId) {
        return subscriptionService.voidPayment(gymId, paymentId);
    }
}
