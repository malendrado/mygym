package com.cortesdev.mygym.models.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record GymClosureResponse(
        Long id,
        LocalDate startDate,
        LocalDate endDate,
        boolean wholeDays,
        List<Long> blockIds,
        String reason,
        String createdByEmail,
        String createdByRole,
        Instant createdAt,
        Instant liftedAt,
        boolean active,
        boolean editable,
        int cancelledReservationsCount,
        int affectedMembersCount,
        int emailsSent,
        int emailsFailed) {}
