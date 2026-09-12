package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record GymCreateRequest(
        @NotBlank String name,
        @NotBlank @Pattern(regexp = "^[a-z0-9-]{3,80}$") String slug,
        @NotNull @Positive Integer maxUsers,
        @NotBlank String ownerName,
        @NotBlank @Email String ownerEmail,
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String themeColor,
        @Size(max = 20000) String logoSvg) {}
