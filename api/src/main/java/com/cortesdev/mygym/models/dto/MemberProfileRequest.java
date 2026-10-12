package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Los cuatro datos juntos, siempre completos: el socio (o el admin) nunca guarda un perfil a medias.
 *  La validación de fondo (dígito verificador, edad, formato de celular) vive en MemberProfileRules. */
public record MemberProfileRequest(
        @NotBlank @Size(max = 20) String rut,
        @NotBlank @Size(max = 300) String address,
        @NotNull LocalDate birthDate,
        @NotBlank @Size(max = 30) String phone) {}
