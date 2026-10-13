package com.cortesdev.mygym.models.dto;

import java.time.LocalDate;

/**
 * Vista previa de activar un plan con esa fecha y esas clases ya usadas (solo lectura): el período que quedaría
 * vigente y cuántas clases le quedarían. bookedInApp = reservas que el socio YA tiene en la app dentro de ese
 * período (cuentan contra el cupo, igual que al reservar). remaining es null en un plan sin tope de clases.
 */
public record PaymentPreviewResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        Integer monthlyClasses,
        int usedOutside,
        int bookedInApp,
        Integer remaining,
        boolean expiringSoon) {}
