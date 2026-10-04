package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Edición de un cierre ya creado — a propósito solo endDate+reason: el alcance (wholeDays/
 *  blockIds) y startDate quedan fijos desde la creación (ver GymClosureService.update). */
public record GymClosureUpdateRequest(@NotNull LocalDate endDate, @NotNull @Size(min = 1, max = 500) String reason) {}
