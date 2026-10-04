package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record GymClosureCreateRequest(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        boolean wholeDays,
        List<Long> blockIds,
        @NotNull @Size(min = 1, max = 500) String reason) {}
