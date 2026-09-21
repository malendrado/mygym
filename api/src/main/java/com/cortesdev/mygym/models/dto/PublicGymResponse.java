package com.cortesdev.mygym.models.dto;

/** Branding-only view of a gym for the public join page (mygym.cl/j/{slug}) — never exposes maxUsers/timestamps/internal ids beyond what the page needs. */
public record PublicGymResponse(
        String name,
        String slug,
        String themeColor,
        String themeContrast,
        String themeMode,
        String logoSvg,
        boolean googleLoginEnabled,
        String tagline,
        String description,
        String instagramUrl,
        String whatsappNumber,
        int cancellationWindowHours) {}
