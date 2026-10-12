package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.dto.FixedClassImportMemberResult;
import com.cortesdev.mygym.models.dto.FixedClassImportRequest;
import com.cortesdev.mygym.models.dto.FixedClassResponse;
import com.cortesdev.mygym.models.dto.FixedClassRowResult;
import com.cortesdev.mygym.models.dto.FixedClassesSaveResponse;
import com.cortesdev.mygym.models.dto.FixedClassesUpdateRequest;
import com.cortesdev.mygym.security.AuthenticatedUser;
import com.cortesdev.mygym.services.FixedClassImportService;
import com.cortesdev.mygym.services.FixedClassService;
import jakarta.validation.Valid;
import java.util.List;
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

/** Clases fijas por socio — lado GYM_ADMIN y PROFESOR (ver SecurityConfig). La contraparte
 *  SUPER_ADMIN vive en GymController (gymId por path). El gym SIEMPRE sale del JWT, nunca del body. */
@RestController
@RequestMapping("/api/gym-admin")
@RequiredArgsConstructor
public class FixedClassController {

    private final FixedClassService fixedClassService;
    private final FixedClassImportService fixedClassImportService;

    @GetMapping("/members/{memberId}/fixed-classes")
    public List<FixedClassResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable Long memberId) {
        return fixedClassService.list(AuthenticatedUser.from(jwt).gymId(), memberId);
    }

    @PutMapping("/members/{memberId}/fixed-classes")
    public FixedClassesSaveResponse replace(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long memberId,
            @Valid @RequestBody FixedClassesUpdateRequest request) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        return fixedClassService.replaceForMember(user.gymId(), memberId, request.blockIds(), user.userId());
    }

    /** Vista previa del Excel: valida todo SIN escribir nada. */
    @PostMapping("/fixed-classes/import/validate")
    public List<FixedClassRowResult> validate(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody FixedClassImportRequest request) {
        return fixedClassImportService.validate(AuthenticatedUser.from(jwt).gymId(), request.rows());
    }

    @PostMapping("/fixed-classes/import")
    public List<FixedClassImportMemberResult> importRows(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody FixedClassImportRequest request) {
        AuthenticatedUser user = AuthenticatedUser.from(jwt);
        return fixedClassImportService.importRows(user.gymId(), request.rows(), user.userId());
    }
}
