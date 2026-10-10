package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Pestaña Horarios del panel del gym — reemplaza al único campo
 *  "cancellationWindowHours" que antes servía a la vez para reservar y para cancelar (ver V36). */
public record BookingRulesRequest(
        @NotNull @Min(0) @Max(10_080) Integer bookingWindowMinutes,
        @NotNull @Min(0) @Max(10_080) Integer cancellationWindowMinutes,
        @NotNull @Min(0) @Max(10_080) Integer waitlistHeadStartMinutes,
        @NotNull Boolean showAttendeesToMembers) {}
