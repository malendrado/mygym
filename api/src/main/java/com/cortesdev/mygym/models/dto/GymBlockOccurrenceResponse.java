package com.cortesdev.mygym.models.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

public record GymBlockOccurrenceResponse(
        Long gymBlockId,
        String label,
        LocalDate classDate,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        Integer capacity,
        Integer taken,
        boolean bookable,
        Long myReservationId) {}
