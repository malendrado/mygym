package com.cortesdev.mygym.models.dto;

import java.time.Instant;

/** Fila de la vista "Administradores" del super-admin (cruza todos los gimnasios) — a diferencia
 *  de AdminResponse (ya usado por GET /{id}/admins, scoped a UN gym), acá cada fila trae su
 *  propio gymName/gymSlug porque la lista mezcla admins de gimnasios distintos. */
public record GymAdminListItemResponse(
        Long id,
        String name,
        String email,
        boolean active,
        String photoUrl,
        Instant createdAt,
        Instant lastLoginAt,
        Long gymId,
        String gymName,
        String gymSlug) {}
