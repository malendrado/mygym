package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.NotBlank;

public record TvScreenClaimRequest(@NotBlank String code, @NotBlank String name) {}
