package com.cortesdev.mygym.models.dto;

import com.cortesdev.mygym.models.CheckinMethod;
import com.cortesdev.mygym.models.ExerciseLogEntry;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Tablero "En clase" para el profesor/admin: las clases que están en curso AHORA (todas, puede haber varias a la
 * vez) y la siguiente, cada una con sus alumnos y la "memoria" de cada uno (qué le toca hoy y qué hizo la última
 * vez). Solo para admins, profesores y super-admin: tiene rutinas y notas, que son datos personales — nunca va
 * a la TV ni a otros socios.
 */
public record ClassBoardResponse(Instant generatedAt, List<BoardClass> classes) {

    /** status: LIVE = en curso ahora; NEXT = la siguiente que empieza (puede ser de otro día). */
    public record BoardClass(
            Long blockId,
            String label,
            String category,
            String instructorName,
            LocalDate classDate,
            LocalTime startTime,
            LocalTime endTime,
            String status,
            Integer capacity,
            List<BoardAttendee> attendees) {}

    public record BoardAttendee(
            Long memberId,
            String name,
            String photoUrl,
            String phone,
            Long reservationId,
            Instant checkedInAt,
            CheckinMethod checkinMethod,
            boolean hasPlan,
            String planName,
            /** Lo que le toca hoy según el ciclo; null si no tiene plan. */
            NextDay nextDay,
            /** Lo último que hizo en una clase anterior; null si no hay registros recientes. */
            LastLog lastLog,
            /** El registro de ESTA clase si ya se anotó (por él o por un profesor); null si no. */
            MemberWorkoutLogResponse thisClassLog,
            /** Vino a su clase anterior y no anotó qué hizo. */
            boolean unloggedLast,
            /** Es su primera reserva en el gimnasio. */
            boolean firstClass) {}

    public record NextDay(Long id, String title, int order, int total) {}

    /** deviated = hizo algo distinto a lo que le tocaba (otro día del plan o texto libre). */
    public record LastLog(
            LocalDate classDate, String dayTitle, boolean deviated, String doneLabel, List<ExerciseLogEntry> exercises) {}
}
