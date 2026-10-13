package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.security.AuthenticatedUser;
import com.cortesdev.mygym.services.PushSubscriptionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Suscripcion a notificaciones push. Vive en /api/push (y no en /api/me) porque tambien la usan el admin del gimnasio y
 * los profesores; SecurityConfig la limita a MEMBER, GYM_ADMIN y PROFESOR (nunca SUPER_ADMIN ni DEMO_ADMIN).
 */
@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushController {

    public record Keys(@NotBlank @Size(max = 200) String p256dh, @NotBlank @Size(max = 100) String auth) {}

    public record SubscriptionRequest(@NotBlank @Size(max = 1000) String endpoint, @NotNull @Valid Keys keys) {}

    public record UnsubscribeRequest(@NotBlank @Size(max = 1000) String endpoint) {}

    private final PushSubscriptionService subscriptionService;

    /** Clave publica VAPID que el navegador necesita para suscribirse; 204 si el servidor no tiene push configurado. */
    @GetMapping("/public-key")
    public ResponseEntity<Map<String, String>> publicKey() {
        String key = subscriptionService.publicKey();
        if (key == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(Map.of("publicKey", key));
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<Void> subscribe(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SubscriptionRequest request, HttpServletRequest http) {
        Long userId = AuthenticatedUser.from(jwt).userId();
        subscriptionService.register(
                userId, request.endpoint(), request.keys().p256dh(), request.keys().auth(), http.getHeader("User-Agent"));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/subscriptions")
    public ResponseEntity<Void> unsubscribe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UnsubscribeRequest request) {
        subscriptionService.unregister(AuthenticatedUser.from(jwt).userId(), request.endpoint());
        return ResponseEntity.noContent().build();
    }

    /** Manda "Avisos activados" a los dispositivos de quien lo pide; maximo 3 por hora. */
    @PostMapping("/test")
    public ResponseEntity<Void> test(@AuthenticationPrincipal Jwt jwt) {
        boolean sent = subscriptionService.sendTest(AuthenticatedUser.from(jwt).userId());
        return sent ? ResponseEntity.accepted().build() : ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
    }
}
