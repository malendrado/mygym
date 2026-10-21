package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.dto.GymContractDtos.CodeRequest;
import com.cortesdev.mygym.models.dto.GymContractDtos.CodeResponse;
import com.cortesdev.mygym.models.dto.GymContractDtos.ContractSummary;
import com.cortesdev.mygym.models.dto.GymContractDtos.ContractView;
import com.cortesdev.mygym.models.dto.GymContractDtos.SignRequest;
import com.cortesdev.mygym.security.AuthRateLimitFilter;
import com.cortesdev.mygym.security.AuthenticatedUser;
import com.cortesdev.mygym.services.ContractService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Firma del contrato de servicios por el administrador del gimnasio (solo GYM_ADMIN, la regla de /subscription/** ya lo exige y
 * deja pasar estas rutas aunque el gimnasio este suspendido por no pago). El gimnasio sale SIEMPRE del JWT.
 */
@RestController
@RequestMapping("/api/gym-admin/subscription/contract")
@RequiredArgsConstructor
public class GymAdminContractController {

    private final ContractService contractService;

    @GetMapping
    public ContractView get(@AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        return contractService.view(user.gymId(), user.userId());
    }

    /** Envia el codigo de 6 digitos al correo del administrador que firma. */
    @PostMapping("/code")
    public CodeResponse sendCode(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CodeRequest request) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        return contractService.sendCode(user.gymId(), user.userId(), request);
    }

    @PostMapping("/sign")
    public ContractSummary sign(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SignRequest request, HttpServletRequest http) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        return contractService.sign(
                user.gymId(), user.userId(), request.code(), AuthRateLimitFilter.clientIp(http), http.getHeader(HttpHeaders.USER_AGENT));
    }

    /** Constancia del contrato firmado (pagina HTML para guardar o imprimir). */
    @GetMapping("/copy")
    public ResponseEntity<String> copy(@AuthenticationPrincipal Jwt jwt) {
        return html(contractService.copyHtml(AuthenticatedUser.from(jwt).gymId()));
    }

    static ResponseEntity<String> html(String body) {
        return ResponseEntity.ok().contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8)).body(body);
    }
}
