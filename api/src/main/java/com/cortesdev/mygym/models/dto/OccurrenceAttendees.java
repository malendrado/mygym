package com.cortesdev.mygym.models.dto;

import com.cortesdev.mygym.models.AppUser;
import java.time.LocalDate;
import java.util.List;

/**
 * Transporte interno servicio→controller para el batch de Historial — a propósito NO es un
 * DTO de respuesta HTTP (lleva entidades AppUser completas), cada controller decide qué campos
 * exponer igual que ya hace con getOccurrenceAttendees(). Ver ReservationService.getOccurrenceAttendeesForRange.
 */
public record OccurrenceAttendees(Long gymBlockId, LocalDate classDate, List<AppUser> attendees) {}
