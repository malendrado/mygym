package com.cortesdev.mygym.models.dto;

import java.util.List;

/** Resultado de guardar las clases fijas de un socio: cómo quedaron + qué pasó con las reservas. */
public record FixedClassesSaveResponse(
        List<FixedClassResponse> classes,
        int created,
        List<FixedClassSkipped> skipped,
        /** Reservas futuras canceladas porque se quitó su clase fija. */
        int cancelled,
        boolean noActivePlan,
        boolean stoppedByQuota) {}
