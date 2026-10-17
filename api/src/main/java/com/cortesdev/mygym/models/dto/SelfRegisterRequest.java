package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Los topes de largo importan para la seguridad: auth_token.pending_name es VARCHAR(255), y un nombre más largo hacía fallar
// el insert solo cuando el email era nuevo (un oráculo para saber qué emails ya tienen cuenta).
public record SelfRegisterRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 200) String email,
        @NotBlank @Size(max = 100) String gymSlug) {}
