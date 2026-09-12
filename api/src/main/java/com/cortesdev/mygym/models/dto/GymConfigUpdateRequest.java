package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record GymConfigUpdateRequest(
        @NotNull Boolean active,
        @NotNull @Positive Integer maxUsers,
        @NotNull Boolean googleLoginEnabled,
        @Size(max = 20000) String logoSvg) {}
