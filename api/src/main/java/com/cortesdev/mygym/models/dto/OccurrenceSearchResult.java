package com.cortesdev.mygym.models.dto;

import java.util.List;

/** Resultado interno de ReservationService.searchUpcomingReservations (antes de armar la respuesta HTTP). */
public record OccurrenceSearchResult(List<OccurrenceAttendees> occurrences, boolean truncated) {}
