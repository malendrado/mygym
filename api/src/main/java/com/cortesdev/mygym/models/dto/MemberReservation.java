package com.cortesdev.mygym.models.dto;

import com.cortesdev.mygym.models.AppUser;

/**
 * Empareja un socio con el id de SU reserva puntual — transporte interno servicio→controller,
 * igual que OccurrenceAttendees (no es un DTO de respuesta HTTP). Permite a los controllers
 * admin construir un AttendeeResponse con reservationId, necesario para poder cancelar esa
 * reserva puntual (ver ReservationService.getOccurrenceAttendees/getOccurrenceAttendeesForRange).
 */
public record MemberReservation(AppUser member, Long reservationId) {}
