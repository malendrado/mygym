package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.NotNull;

public record AdminStatusUpdateRequest(@NotNull Boolean active) {}
