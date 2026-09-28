package com.cortesdev.mygym.models.dto;

import java.time.Instant;

/** Código rotativo que la TV muestra como QR — ver TvCheckinCode. */
public record TvCheckinCodeResponse(String code, Instant expiresAt) {}
