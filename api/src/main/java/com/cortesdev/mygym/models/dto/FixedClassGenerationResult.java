package com.cortesdev.mygym.models.dto;

import java.util.List;

public record FixedClassGenerationResult(
        int created,
        List<FixedClassSkipped> skipped,
        /** true = el socio no tiene plan activo: las clases fijas quedan guardadas, sin reservas todavía. */
        boolean noActivePlan,
        /** true = se cortó antes de terminar el período porque ya no le quedaban clases del plan. */
        boolean stoppedByQuota) {

    public static FixedClassGenerationResult none(boolean noActivePlan) {
        return new FixedClassGenerationResult(0, List.of(), noActivePlan, false);
    }
}
