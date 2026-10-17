package com.cortesdev.mygym.models.dto;

import java.time.LocalDate;
import java.util.List;

/** Una ventana del historial de rutina (los registros más recientes primero). `nextBefore` es el cursor para pedir la
 *  ventana anterior (`?before=`); null cuando no hay registros más antiguos. */
public record WorkoutLogPageResponse(List<MemberWorkoutLogResponse> logs, LocalDate nextBefore) {}
