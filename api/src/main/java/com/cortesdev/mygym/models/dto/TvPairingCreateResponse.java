package com.cortesdev.mygym.models.dto;

import java.time.Instant;

public record TvPairingCreateResponse(String code, Instant expiresAt) {}
