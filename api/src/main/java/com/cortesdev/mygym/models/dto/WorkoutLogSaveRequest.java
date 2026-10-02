package com.cortesdev.mygym.models.dto;

import com.cortesdev.mygym.models.ExerciseLogEntry;
import java.util.List;

/** Exactamente uno de planDayId/freeTextLabel debe venir — el socio acepta la sugerencia (manda
 *  planDayId) o escribe libre con "Otro" (manda freeTextLabel), ver WorkoutService. Usado tanto
 *  para crear el log de una reserva como para corregirlo después. */
public record WorkoutLogSaveRequest(Long planDayId, String freeTextLabel, List<ExerciseLogEntry> exercises) {}
