package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * `logo` holds either raw sanitized SVG markup (starts with "<svg") or a
 * raster image as a data URI (starts with "data:image/") — see
 * GymService#updateMyLogo for the branch that decides which.
 */
public record GymLogoUpdateRequest(@NotBlank @Size(max = 400000) String logo) {}
