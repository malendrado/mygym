package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.services.FlowPaymentService;
import com.cortesdev.mygym.services.GymSubscriptionFlowService;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Flow.cl solo manda un "token" por POST form-urlencoded, nunca un body confiable — el estado
 *  real siempre se reconsulta llamando de vuelta a la API de Flow con ese token (ver
 *  FlowPaymentService.handleWebhook). Público automático por caer bajo /api/public/**
 *  (SecurityConfig), sin tocar nada de seguridad. Flow exige responder 200 en menos de 15s
 *  para el webhook — el return NO tiene ese requisito. */
@RestController
@RequestMapping("/api/public/flow")
public class PublicFlowController {

    private final FlowPaymentService flowPaymentService;
    private final GymSubscriptionFlowService subscriptionFlowService;
    private final String memberReturnUrl;
    private final String subscriptionReturnUrl;

    public PublicFlowController(
            FlowPaymentService flowPaymentService,
            GymSubscriptionFlowService subscriptionFlowService,
            @Value("${app.flow.member-return-url}") String memberReturnUrl,
            @Value("${app.mygym-flow.member-return-url}") String subscriptionReturnUrl) {
        this.flowPaymentService = flowPaymentService;
        this.subscriptionFlowService = subscriptionFlowService;
        this.memberReturnUrl = memberReturnUrl;
        this.subscriptionReturnUrl = subscriptionReturnUrl;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestParam String token) {
        flowPaymentService.handleWebhook(token);
        return ResponseEntity.ok().build();
    }

    // Pago de la suscripcion de un gimnasio a mygym (cuenta Flow propia de mygym): mismo mecanismo, otro destino.
    @PostMapping("/mygym-webhook")
    public ResponseEntity<Void> subscriptionWebhook(@RequestParam String token) {
        subscriptionFlowService.handleWebhook(token);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/mygym-return")
    public ResponseEntity<Void> subscriptionReturn() {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(subscriptionReturnUrl)).build();
    }

    // Flow devuelve al socio con un POST del navegador (documentado así, no un GET) — no
    // puede apuntar directo a la SPA en Vercel, que solo sirve GET ahí (un POST da 405, bug
    // real encontrado probando el flujo end-to-end). Este endpoint solo rebota con un 302 a
    // la SPA de verdad, vía GET, que el navegador sigue solo. No hace falta tocar el token acá
    // — la confirmación real ya la hizo (o está por hacer) el webhook server-to-server.
    @PostMapping("/return")
    public ResponseEntity<Void> checkoutReturn() {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(memberReturnUrl)).build();
    }
}
