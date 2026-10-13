package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** bank: para los informes posteriores — ver ManualPayment. Obligatorio, el frontend usa el
 *  mismo selector CHILE_BANKS que los datos bancarios del gym ("Otro" manda texto libre).
 *  startDate: desde cuándo vale el plan (null = ahora; nunca futura ni de hace más de un mes) y
 *  usedClasses: clases que el socio ya usó fuera de la app en ese período (null/0 = ninguna; se ignora en
 *  planes libres). Ver GymService.simulatePlanPayment. */
public record MarkPaidRequest(
        @NotNull Long planId,
        @NotBlank @Size(max = 100) String bank,
        LocalDate startDate,
        @Min(0) @Max(1000) Integer usedClasses) {}
