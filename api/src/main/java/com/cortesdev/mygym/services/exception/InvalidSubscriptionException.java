package com.cortesdev.mygym.services.exception;

/** Dato invalido al operar la suscripcion de un gimnasio a mygym (400). El mensaje llega tal cual a la pantalla: en espanol. */
public class InvalidSubscriptionException extends RuntimeException {

    public InvalidSubscriptionException(String message) {
        super(message);
    }
}
