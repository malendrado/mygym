package com.cortesdev.mygym.models.dto;

import java.util.List;

/**
 * Respuesta del buscador de reservas futuras por nombre de socio. `truncated` = true cuando el
 * texto coincidió con más socios que el tope (ver ReservationService.MAX_SEARCH_MEMBERS) y solo
 * se devolvieron las reservas de los primeros — el frontend avisa que conviene afinar la búsqueda.
 */
public record ReservationSearchResponse(List<BlockOccurrenceAttendeesResponse> results, boolean truncated) {}
