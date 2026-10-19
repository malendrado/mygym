package com.cortesdev.mygym.services.exception;

/** La accion no corresponde al estado actual de la suscripcion (409). El mensaje llega tal cual a la pantalla: en espanol. */
public class SubscriptionConflictException extends RuntimeException {

    public SubscriptionConflictException(String message) {
        super(message);
    }
}
