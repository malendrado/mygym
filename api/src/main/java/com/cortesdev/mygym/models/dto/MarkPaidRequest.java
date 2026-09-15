package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.NotNull;

public record MarkPaidRequest(@NotNull Long planId) {}
