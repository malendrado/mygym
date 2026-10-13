package com.cortesdev.mygym.models.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * Resumen de asistencia de un período (semana o mes) — solo para admins, profesores y super-admin.
 * Solo cuenta clases YA terminadas: lo que sigue en curso no es "falta" todavía. La tasa
 * (attended / booked) la calcula el frontend; con booked = 0 no hay tasa.
 */
public record AttendanceSummaryResponse(
        Totals totals,
        Totals previousTotals,
        List<ClassStat> byClass,
        List<WeekdayStat> byWeekday,
        List<MemberAttendanceRow> topAbsentees,
        /** Los que más asistieron (mínimo 3 reservas), más asistencias primero. */
        List<MemberAttendanceRow> topAttendees) {

    public record Totals(int booked, int attended, int missed) {}

    /** Una fila por clase (bloque semanal) con reservas terminadas en el período, de peor a mejor tasa. */
    public record ClassStat(
            Long blockId, String label, DayOfWeek weekday, LocalTime startTime, int booked, int attended) {}

    /** Los 7 días, de lunes a domingo (booked = 0 si ese día no hubo clases terminadas). */
    public record WeekdayStat(DayOfWeek weekday, int booked, int attended) {}
}
