package com.cortesdev.mygym.models;

/** Un elemento de la lista `exercises_log` (JSONB) de un MemberWorkoutLog — "qué hizo" en esa
 *  clase, sin catálogo ni estructura prescrita a propósito (bloc de notas). */
public record ExerciseLogEntry(String exercise, String notes) {}
