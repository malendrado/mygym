package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** bank: para los informes posteriores — ver ManualPayment. Obligatorio, el frontend usa el
 *  mismo selector CHILE_BANKS que los datos bancarios del gym ("Otro" manda texto libre). */
public record MarkPaidRequest(@NotNull Long planId, @NotBlank @Size(max = 100) String bank) {}
