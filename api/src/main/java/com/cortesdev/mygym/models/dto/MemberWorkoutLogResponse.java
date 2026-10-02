package com.cortesdev.mygym.models.dto;

import com.cortesdev.mygym.models.ExerciseLogEntry;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** suggestedPlanDayTitle/planDayTitle vienen resueltos acá para que el frontend no tenga que
 *  cruzar IDs contra la lista de días del plan — si difieren (o planDayId es null), el socio se
 *  desvió de la sugerencia, el frontend arma el mensaje ("te tocaba X pero hizo Y") con esto. */
public record MemberWorkoutLogResponse(
        Long id,
        Long reservationId,
        LocalDate classDate,
        Long suggestedPlanDayId,
        String suggestedPlanDayTitle,
        Long planDayId,
        String planDayTitle,
        String freeTextLabel,
        List<ExerciseLogEntry> exercises,
        Instant createdAt) {}
