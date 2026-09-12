package com.cortesdev.mygym.models.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record BlockResponse(
        Long id,
        Long gymId,
        String label,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        Integer capacity,
        boolean active) {}
