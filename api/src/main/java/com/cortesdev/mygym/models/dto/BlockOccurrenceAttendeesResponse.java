package com.cortesdev.mygym.models.dto;

import java.time.LocalDate;
import java.util.List;

/** Una entrada por cada ocurrencia de bloque con al menos un asistente, dentro del rango
 *  pedido — respuesta del endpoint batch de Historial (reemplaza N llamadas a
 *  GET .../blocks/{id}/occurrences/{date}/attendees por una sola). */
public record BlockOccurrenceAttendeesResponse(Long gymBlockId, LocalDate classDate, List<AttendeeResponse> attendees) {}
