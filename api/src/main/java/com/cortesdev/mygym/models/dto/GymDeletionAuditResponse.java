package com.cortesdev.mygym.models.dto;

import java.time.Instant;

public record GymDeletionAuditResponse(
        Long id,
        String gymName,
        String gymSlug,
        String adminEmails,
        int memberCount,
        int reservationCount,
        int paymentCount,
        int blockCount,
        int planCount,
        int photoCount,
        String executedBy,
        Instant executedAt) {}
