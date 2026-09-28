package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckinRequest(@NotBlank String code) {}
