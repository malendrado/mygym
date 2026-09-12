package com.cortesdev.mygym.models.dto;

import java.time.Instant;

public record GymResponse(
        Long id,
        String name,
        String slug,
        boolean active,
        Integer maxUsers,
        boolean googleLoginEnabled,
        String themeColor,
        String logoSvg,
        Instant createdAt,
        Instant updatedAt) {}
