package com.cortesdev.mygym.models.dto;

/** screenToken es null hasta que claimed sea true. */
public record TvPairingStatusResponse(boolean claimed, String screenToken) {}
