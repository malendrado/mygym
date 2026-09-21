package com.cortesdev.mygym.models.dto;

/** Visitas a la página pública de alta (/j/{slug}) de un gimnasio puntual. */
public record GymVisitStats(Long gymId, String gymName, String gymSlug, long total, long last30d) {}
