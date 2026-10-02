package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SelfRegisterRequest(
        @NotBlank String name, @NotBlank @Email String email, @NotBlank String gymSlug) {}
