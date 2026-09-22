package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// Flow rechaza cualquier pago menor a 350 CLP ("The minimum amount is 350 CLP") — un plan
// más barato que eso nunca se puede cobrar de verdad, así que se bloquea acá antes de que
// el admin llegue a guardarlo (ver FlowPaymentService, incidente real con un plan a $10).
public record PlanCreateRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 280) String description,
        @Size(max = 30) String category,
        @NotNull @Min(value = 350, message = "El precio mínimo es $350 (Flow no procesa pagos por menos)") Integer priceClp,
        @Positive Integer monthlyClasses) {}
