package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AdminCreateRequest(@NotBlank String name, @NotBlank @Email String email) {}
