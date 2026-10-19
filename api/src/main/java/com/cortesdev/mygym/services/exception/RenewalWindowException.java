package com.cortesdev.mygym.services.exception;

/** El socio todavia tiene plan vigente y aun no esta en los ultimos dias para renovar (409). El mensaje llega tal cual a la pantalla: en espanol. */
public class RenewalWindowException extends RuntimeException {

    public RenewalWindowException(String message) {
        super(message);
    }
}
