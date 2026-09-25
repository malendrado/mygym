package com.cortesdev.mygym.models.dto;

import java.time.Instant;

/** Null en lastPolledAt = nunca hizo un poll real todavía (recién vinculada). */
public record TvScreenResponse(Long id, String name, Instant createdAt, Instant lastPolledAt) {}
