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
        boolean expiringSoon,
        /** true si pagar ahora renueva por adelantado: periodStart/periodEnd son los del período NUEVO (parte en el vencimiento actual). */
        boolean earlyRenewal,
        /** Meses completos ya pagados por adelantado (en cola) que este pago descartaría; 0 si no se pierde nada. */
        int lostPaidMonths,
        /** Días que le quedaban del período actual y este pago descartaría (reinicia la cadena sobre un plan vigente); 0 si no se pierde nada. */
        long lostDays,
        /** true si el pago es un cambio de plan que queda en espera: no reinicia nada, el plan nuevo parte cuando se acaben las clases o llegue el vencimiento. */
        boolean planChangeScheduled,
        /** Vencimiento actual = fecha tope en que partiría el plan nuevo; null si no es un cambio programado. */
        LocalDate planChangeFrom) {}
