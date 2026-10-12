package com.cortesdev.mygym.models.dto;

import java.time.LocalDate;

/** Asistencia de un socio en un período: reservas terminadas, cuántas vino y cuántas no. */
public record MemberAttendanceRow(
        Long memberId,
        String name,
        String photoUrl,
        String phone,
        int booked,
        int attended,
        int missed,
        LocalDate lastMissedDate) {}
