package com.cortesdev.mygym.models.dto;

import com.cortesdev.mygym.models.AppUser;
import java.time.Instant;

/**
 * Empareja un socio con el id de SU reserva puntual — transporte interno servicio→controller,
 * igual que OccurrenceAttendees (no es un DTO de respuesta HTTP). Permite a los controllers
 * admin construir un AttendeeResponse con reservationId, necesario para poder cancelar esa
 * reserva puntual (ver ReservationService.getOccurrenceAttendees/getOccurrenceAttendeesForRange).
 * checkedInAt viaja para que la TV pueda marcar quién ya confirmó asistencia (ver
 * TvScreenService.toSummary) — null si todavía no escaneó el QR de la clase.
 */
public record MemberReservation(AppUser member, Long reservationId, Instant checkedInAt) {}
