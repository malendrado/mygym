package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.services.FlowPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Flow.cl solo manda un "token" por POST form-urlencoded, nunca un body confiable — el estado
 *  real siempre se reconsulta llamando de vuelta a la API de Flow con ese token (ver
 *  FlowSubscriptionService.handleWebhook). Público automático por caer bajo /api/public/**
 *  (SecurityConfig), sin tocar nada de seguridad. Flow exige responder 200 en menos de 15s. */
@RestController
@RequestMapping("/api/public/flow")
@RequiredArgsConstructor
public class PublicFlowController {

    private final FlowPaymentService flowPaymentService;

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestParam String token) {
        flowPaymentService.handleWebhook(token);
        return ResponseEntity.ok().build();
    }
}
