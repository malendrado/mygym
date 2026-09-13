package com.cortesdev.mygym.models.dto;

public record PlanResponse(
        Long id,
        Long gymId,
        String name,
        String description,
        String category,
        Integer priceClp,
        Integer monthlyClasses,
        boolean active) {}
