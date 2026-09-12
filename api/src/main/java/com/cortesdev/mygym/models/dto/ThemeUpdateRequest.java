package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ThemeUpdateRequest(@NotBlank @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String themeColor) {}
