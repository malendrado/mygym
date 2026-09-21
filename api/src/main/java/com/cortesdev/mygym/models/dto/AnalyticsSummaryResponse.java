package com.cortesdev.mygym.models.dto;

import java.util.List;

public record AnalyticsSummaryResponse(
        PageStats brochure,
        PageStats landing,
        PageStats joinTotal,
        /** Ordenada de mayor a menor por `total`. */
        List<GymVisitStats> byGym) {}
