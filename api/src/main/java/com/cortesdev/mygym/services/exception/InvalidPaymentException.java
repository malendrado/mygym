package com.cortesdev.mygym.services.exception;

/** Fecha de inicio o clases usadas inválidas al activar un plan a mano — 400. */
public class InvalidPaymentException extends RuntimeException {

    public InvalidPaymentException(String message) {
        super(message);
    }
}
