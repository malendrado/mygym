package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.NotBlank;

/** confirmGymName tiene que coincidir EXACTO con el nombre real del gimnasio — mismo patrón de
 *  "escribe el nombre para confirmar" que usan GitHub/Vercel para acciones irreversibles. */
public record GymDisconnectRequest(@NotBlank String confirmGymName) {}
