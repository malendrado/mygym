package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ReservationCreateRequest(@NotNull Long gymBlockId, @NotNull LocalDate classDate) {}
