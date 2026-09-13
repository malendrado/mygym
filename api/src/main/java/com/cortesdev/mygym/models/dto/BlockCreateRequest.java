package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record BlockCreateRequest(
        @NotBlank String label,
        @NotNull DayOfWeek dayOfWeek,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotNull @Positive Integer capacity,
        @Size(max = 40) String category,
        @Size(max = 80) String instructorName,
        String instructorPhoto) {}
