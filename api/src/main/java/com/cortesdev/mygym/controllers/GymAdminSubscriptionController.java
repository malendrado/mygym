package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.dto.GymSubscriptionDtos.BillingInfoRequest;
import com.cortesdev.mygym.models.dto.GymSubscriptionDtos.CheckoutResponse;
import com.cortesdev.mygym.models.dto.GymSubscriptionDtos.ReportTransferRequest;
import com.cortesdev.mygym.models.dto.GymSubscriptionDtos.SubscriptionResponse;
import com.cortesdev.mygym.security.AuthenticatedUser;
import com.cortesdev.mygym.services.GymSubscriptionFlowService;
import com.cortesdev.mygym.services.GymSubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Suscripcion del propio gimnasio a mygym, vista por su administrador (solo GYM_ADMIN: no profesores ni demo). El gimnasio
 * sale SIEMPRE del JWT, nunca de la URL. Estas rutas son las unicas que el filtro de pausa deja pasar cuando el gimnasio esta
 * suspendido por no pago, para que pueda pagar y reactivarse (ver GymPauseFilter).
 */
@RestController
@RequestMapping("/api/gym-admin/subscription")
@RequiredArgsConstructor
public class GymAdminSubscriptionController {

    private final GymSubscriptionService subscriptionService;
    private final GymSubscriptionFlowService flowService;

    @GetMapping
    public SubscriptionResponse get(@AuthenticationPrincipal Jwt jwt) {
        return subscriptionService.get(AuthenticatedUser.from(jwt).gymId());
    }

    /** Arranca el pago con Flow; devuelve la URL a la que hay que llevar al administrador. */
    @PostMapping("/checkout")
    public CheckoutResponse checkout(@AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        return new CheckoutResponse(flowService.startCheckout(user.gymId(), user.userId()));
    }

    /** "Ya transferí": avisa a mygym para que confirme la transferencia. */
    @PostMapping("/transfer-report")
    public SubscriptionResponse reportTransfer(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReportTransferRequest request) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        return subscriptionService.reportTransfer(user.gymId(), user.userId(), request);
    }

    @PutMapping("/billing")
    public SubscriptionResponse updateBilling(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BillingInfoRequest request) {
        return subscriptionService.updateOwnBilling(AuthenticatedUser.from(jwt).gymId(), request);
    }
}
