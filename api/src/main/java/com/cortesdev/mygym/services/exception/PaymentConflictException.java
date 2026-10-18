package com.cortesdev.mygym.services.exception;

/** Pago manual rechazado con 409: o necesita confirmación explícita (descarta meses ya pagados por adelantado) o es un
 *  reintento idéntico de hace segundos. {@code code} deja al front distinguir los dos casos sin leer el texto. */
public class PaymentConflictException extends RuntimeException {

    public static final String CODE_LOSE_QUEUED_MONTHS = "LOSE_QUEUED_MONTHS";
    public static final String CODE_DUPLICATE_PAYMENT = "DUPLICATE_PAYMENT";

    private final String code;

    public PaymentConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
