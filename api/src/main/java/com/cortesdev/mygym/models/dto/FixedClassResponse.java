package com.cortesdev.mygym.models.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

/** Una clase fija de un socio, con los datos del bloque para mostrarla sin otra consulta. */
public record FixedClassResponse(
        Long blockId,
        String label,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        String category,
        String instructorName,
        /** false = el bloque se desactivó: la clase fija sigue guardada pero no genera reservas. */
        boolean active) {}
