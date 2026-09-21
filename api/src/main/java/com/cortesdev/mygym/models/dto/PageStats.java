package com.cortesdev.mygym.models.dto;

/** Conteo de visitas a una superficie pública: histórico, últimos 7 días y últimos 30 días. */
public record PageStats(long total, long last7d, long last30d) {}
