package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PlanUpdateRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 280) String description,
        @Size(max = 30) String category,
        @NotNull @Min(0) Integer priceClp,
        @Positive Integer monthlyClasses,
        @NotNull Boolean active) {}
