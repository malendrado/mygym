package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GymIdentityUpdateRequest(
        @Size(max = 160) String tagline,
        @Size(max = 600) String description,
        @Size(max = 200) String instagramUrl,
        @Size(max = 30) String whatsappNumber,
        @NotNull @Min(1) Integer cancellationWindowHours) {}
