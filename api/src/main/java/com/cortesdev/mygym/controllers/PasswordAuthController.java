package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.dto.ActivateAccountRequest;
import com.cortesdev.mygym.models.dto.ForgotPasswordRequest;
import com.cortesdev.mygym.models.dto.LoginResponse;
import com.cortesdev.mygym.models.dto.MessageResponse;
import com.cortesdev.mygym.models.dto.PasswordLoginRequest;
import com.cortesdev.mygym.models.dto.SelfRegisterRequest;
import com.cortesdev.mygym.models.dto.TokenInfoResponse;
import com.cortesdev.mygym.services.PasswordAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Email + contraseña, totalmente público (igual que PublicGymController) — convive con
 * /api/auth/google, nunca lo reemplaza. requestSelfRegistration/requestPasswordReset devuelven
 * SIEMPRE el mismo mensaje genérico (ver PasswordAuthService): este controller nunca distingue
 * "mandamos el correo" de "ese email no existe/ya tenía cuenta", a propósito.
 */
@RestController
@RequestMapping("/api/public/auth")
@RequiredArgsConstructor
public class PasswordAuthController {

    private static final MessageResponse CHECK_YOUR_EMAIL =
            new MessageResponse("Si los datos son válidos, te enviamos un correo para continuar — revisa tu bandeja de entrada.");

    private final PasswordAuthService passwordAuthService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody PasswordLoginRequest request) {
        return passwordAuthService.login(request.email(), request.password());
    }

    @PostMapping("/register")
    public MessageResponse register(@Valid @RequestBody SelfRegisterRequest request) {
        passwordAuthService.requestSelfRegistration(request.name(), request.email(), request.gymSlug());
        return CHECK_YOUR_EMAIL;
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordAuthService.requestPasswordReset(request.email());
        return CHECK_YOUR_EMAIL;
    }

    @GetMapping("/token/{token}")
    public TokenInfoResponse tokenInfo(@PathVariable String token) {
        return passwordAuthService.tokenInfo(token);
    }

    @PostMapping("/activate")
    public LoginResponse activate(@Valid @RequestBody ActivateAccountRequest request) {
        return passwordAuthService.activate(request.token(), request.password());
    }
}
