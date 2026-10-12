package com.cortesdev.mygym.models.dto;

import com.cortesdev.mygym.models.CheckinMethod;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Detalle de un socio en un período: cada reserva con su fecha, clase y si vino. editable = la
 * clase está dentro de la ventana de marcado a mano (ver ReservationService.setAttendance).
 */
public record MemberAttendanceDetail(MemberAttendanceRow member, List<Row> rows) {

    public record Row(
            Long reservationId,
            LocalDate classDate,
            String blockLabel,
            LocalTime startTime,
            boolean ended,
            boolean attended,
            CheckinMethod checkinMethod,
            Instant checkedInAt,
            boolean editable) {}
}
