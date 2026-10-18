package com.cortesdev.mygym.models.dto;

import java.time.LocalDate;
import java.util.List;

/** Una ocurrencia de la Agenda del admin: cupos, cierre de emergencia, lista de espera y quién reservó.
 *  closedReason es null si la clase no está cerrada. */
public record AgendaOccurrenceResponse(
        Long gymBlockId,
        LocalDate classDate,
        int capacity,
        int taken,
        boolean closed,
        String closedReason,
        int waitlistCount,
        List<WaitlistStaffEntry> waitlist,
        List<AttendeeResponse> attendees) {}
