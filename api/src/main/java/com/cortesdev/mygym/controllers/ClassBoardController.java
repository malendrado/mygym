package com.cortesdev.mygym.controllers;

import com.cortesdev.mygym.models.dto.ClassBoardResponse;
import com.cortesdev.mygym.security.AuthenticatedUser;
import com.cortesdev.mygym.services.ClassBoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tablero "En clase" — lado GYM_ADMIN / PROFESOR / DEMO_ADMIN (solo lectura, ver SecurityConfig: el GET de
 *  /api/gym-admin/** ya los deja pasar). La contraparte SUPER_ADMIN vive en GymController. El gym SIEMPRE sale del JWT. */
@RestController
@RequestMapping("/api/gym-admin")
@RequiredArgsConstructor
public class ClassBoardController {

    private final ClassBoardService classBoardService;

    @GetMapping("/class-board")
    public ClassBoardResponse board(@AuthenticationPrincipal Jwt jwt) {
        return classBoardService.board(AuthenticatedUser.from(jwt).gymId());
    }
}
