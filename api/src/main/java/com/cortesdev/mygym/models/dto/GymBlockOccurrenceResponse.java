package com.cortesdev.mygym.models.dto;

import java.time.DayOfWeek;
import java.time.Instant;
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
        String category,
        String instructorName,
        String instructorPhoto,
        Integer taken,
        boolean bookable,
        boolean past,
        Long myReservationId,
        boolean waitlisted,
        Instant myCheckedInAt,
        boolean closed,
        /** true = mi reserva en esta clase viene de una clase fija mía. */
        boolean myReservationFixed) {}
