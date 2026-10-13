package com.cortesdev.mygym.models.dto;

import com.cortesdev.mygym.models.ExerciseLogEntry;
import java.util.List;

/** Exactamente uno de planDayId/freeTextLabel debe venir — el socio acepta la sugerencia (manda
 *  planDayId) o escribe libre con "Otro" (manda freeTextLabel), ver WorkoutService. Usado tanto
 *  para crear el log de una reserva como para corregirlo después. */
public record WorkoutLogSaveRequest(
        Long planDayId,
        // Topes de tamaño: este texto lo escribe el socio o un profesor y se guarda tal cual (JSONB); sin tope, una
        // sola petición podía guardar megas.
        @jakarta.validation.constraints.Size(max = 200) String freeTextLabel,
        @jakarta.validation.constraints.Size(max = 30) List<@jakarta.validation.Valid ExerciseLogEntry> exercises) {}
