package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** "Redefinir la rutina" manda esto de nuevo: desactiva el plan activo anterior (si había) y
 *  crea uno nuevo con estos días, ver WorkoutService.replacePlan. */
public record WorkoutPlanCreateRequest(
        @NotBlank String name, @NotEmpty @Size(max = 30) List<@NotBlank String> dayTitles) {}
