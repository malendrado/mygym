package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** {@code tour}: "ADMIN" o "MEMBER" — ver DemoTourService.ALLOWED_TOURS. */
public record TourProgressRequest(@NotBlank String tour, @Min(1) int step) {}
