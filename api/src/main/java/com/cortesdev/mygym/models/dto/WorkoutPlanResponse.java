package com.cortesdev.mygym.models.dto;

import java.util.List;

public record WorkoutPlanResponse(Long id, String name, List<PlanDayResponse> days) {}
