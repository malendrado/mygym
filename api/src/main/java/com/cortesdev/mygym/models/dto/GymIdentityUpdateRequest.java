package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.Size;

// cancellationWindowHours salió de acá (ver V36/BookingRulesRequest) — la ventana de
// reserva/cancelación ahora se edita en la pestaña Horarios, no en Mi marca.
public record GymIdentityUpdateRequest(
        @Size(max = 160) String tagline,
        @Size(max = 600) String description,
        @Size(max = 200) String instagramUrl,
        @Size(max = 30) String whatsappNumber) {}
