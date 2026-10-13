package com.cortesdev.mygym.models;

/** Un elemento de la lista `exercises_log` (JSONB) de un MemberWorkoutLog — "qué hizo" en esa
 *  clase, sin catálogo ni estructura prescrita a propósito (bloc de notas). */
public record ExerciseLogEntry(
        @jakarta.validation.constraints.Size(max = 200) String exercise,
        @jakarta.validation.constraints.Size(max = 500) String notes) {}
