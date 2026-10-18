package com.cortesdev.mygym.models.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Transporte interno servicio→controller para la Agenda del admin — como OccurrenceAttendees, NO es un
 * DTO HTTP (lleva AppUser completos dentro de cada MemberReservation). Una entrada por ocurrencia
 * (bloque × fecha), incluidas las que no tienen reservas. Ver ReservationService.getAgenda.
 */
public record AgendaOccurrence(
        Long gymBlockId,
        LocalDate classDate,
        int capacity,
        int taken,
        boolean closed,
        String closedReason,
        int waitlistCount,
        List<WaitlistStaffEntry> waitlist,
        List<MemberReservation> attendees) {}
