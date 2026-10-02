package com.cortesdev.mygym.models.dto;

import java.time.LocalDate;

/** Qué debería ver el socio al entrar a la pestaña "Rutina" — status resuelve los 3 estados
 *  vacíos del diseño (el frontend arma el copy exacto de cada uno):
 *  NO_PLAN = aún no tiene rutina asignada por su profesor.
 *  NO_PENDING = tiene plan, pero no hay ninguna clase asistida sin registrar.
 *  READY = hay una reserva con check-in esperando que la anote (reservationId/classDate/plan/
 *          suggestedPlanDayId vienen completos). */
public record PendingWorkoutResponse(
        String status,
        Long reservationId,
        LocalDate classDate,
        WorkoutPlanResponse plan,
        Long suggestedPlanDayId) {}
