package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.NotBlank;

public record PasswordLoginRequest(@NotBlank String email, @NotBlank String password) {}
