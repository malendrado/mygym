package com.cortesdev.mygym.models.dto;

/**
 * Conteos de las calugas de /admin/gyms (todos los gimnasios, NO solo la página visible) — con la
 * lista paginada ya no se pueden calcular contando tarjetas en el navegador.
 */
public record GymStatsResponse(long total, long active, long totalCapacity, long branded) {}
