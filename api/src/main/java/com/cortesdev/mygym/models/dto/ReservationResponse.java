package com.cortesdev.mygym.models.dto;

import com.cortesdev.mygym.models.ReservationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record ReservationResponse(
        Long id,
        Long gymBlockId,
        String blockLabel,
        LocalDate classDate,
        LocalTime startTime,
        LocalTime endTime,
        ReservationStatus status,
        Instant createdAt) {}
