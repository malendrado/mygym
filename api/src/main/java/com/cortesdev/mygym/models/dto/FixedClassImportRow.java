package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalTime;

/** Una fila del Excel de clases fijas, ya normalizada por el frontend (día y hora legibles).
 *  La clase se identifica por día + hora + NOMBRE — nunca solo por día y hora, porque un gym
 *  puede tener dos clases (ej. GAP y CrossFit) exactamente al mismo horario. */
public record FixedClassImportRow(
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull DayOfWeek dayOfWeek,
        @NotNull LocalTime startTime,
        @NotBlank @Size(max = 200) String className) {}
