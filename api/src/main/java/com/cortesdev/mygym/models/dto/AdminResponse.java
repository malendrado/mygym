package com.cortesdev.mygym.models.dto;

import java.time.Instant;

public record AdminResponse(
        Long id,
        String name,
        String email,
        boolean active,
        String photoUrl,
        /** Cuándo se creó este acceso — para un demo admin, también "cuándo se invitó" (nunca se
         *  toca después). El vencimiento a 7 días de la demo se calcula en el frontend a partir de
         *  este campo (ver DemoAccessService.DEMO_ACCESS_TTL). */
        Instant createdAt,
        /** Null si nunca entró con Google — ver AppUser.lastLoginAt. */
        Instant lastLoginAt,
        /** Última actividad real en la app (un GYM_ADMIN, como mucho una vez por hora); null si todavía no hay registro. Ver AppUser.lastSeenAt. */
        Instant lastSeenAt,
        /** Paso más alto alcanzado en cada tour guiado de la demo — null si todavía no lo abrió.
         *  Siempre null para un admin real (GYM_ADMIN), solo aplica a DEMO_ADMIN. */
        Integer adminTourStep,
        Integer memberTourStep) {}
