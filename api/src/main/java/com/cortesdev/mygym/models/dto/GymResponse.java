package com.cortesdev.mygym.models.dto;

import java.time.Instant;

public record GymResponse(
        Long id,
        String publicId,
        String name,
        String slug,
        boolean active,
        Integer maxUsers,
        boolean googleLoginEnabled,
        String themeColor,
        String themeMode,
        String logoSvg,
        String tagline,
        String description,
        String instagramUrl,
        String whatsappNumber,
        int cancellationWindowHours,
        String bankName,
        String bankAccountType,
        String bankAccountNumber,
        String bankHolderRut,
        String bankHolderName,
        String bankConfirmationEmail,
        Instant createdAt,
        Instant updatedAt) {}
