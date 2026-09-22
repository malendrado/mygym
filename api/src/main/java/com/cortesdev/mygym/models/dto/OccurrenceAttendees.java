package com.cortesdev.mygym.models.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Transporte interno servicio→controller para el batch de Historial/búsqueda — a propósito NO
 * es un DTO de respuesta HTTP (lleva entidades AppUser completas dentro de cada
 * MemberReservation), cada controller decide qué campos exponer igual que ya hace con
 * getOccurrenceAttendees(). Ver ReservationService.getOccurrenceAttendeesForRange/searchUpcomingReservations.
 */
public record OccurrenceAttendees(Long gymBlockId, LocalDate classDate, List<MemberReservation> attendees) {}
