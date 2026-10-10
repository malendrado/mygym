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
        int bookingWindowMinutes,
        int cancellationWindowMinutes,
        /** Si los socios pueden ver el roster de "Ver quién va" en /member — la pantalla de TV
         *  del gimnasio nunca se ve afectada por esto (ver ReservationController/V36). */
        boolean showAttendeesToMembers,
        /** true si el gym tiene su propia cuenta Flow configurada — si es false, el
         *  frontend oculta "Pagar con Flow" y solo ofrece transferencia bancaria. */
        boolean flowConfigured) {}
