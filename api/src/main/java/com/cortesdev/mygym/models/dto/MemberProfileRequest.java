package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Los seis datos (contacto + contacto de emergencia). Con el perfil incompleto deben venir todos; con el perfil ya completo
 *  los nulos/en blanco significan "no cambiar" (por eso aquí no hay @NotBlank/@NotNull: lo decide MemberProfileService).
 *  La validación de fondo (dígito verificador, edad, formato de celular) vive en MemberProfileRules. */
public record MemberProfileRequest(
        @Size(max = 20) String rut,
        @Size(max = 300) String address,
        LocalDate birthDate,
        @Size(max = 30) String phone,
        @Size(max = 100) String emergencyContactName,
        @Size(max = 30) String emergencyContactPhone) {}
