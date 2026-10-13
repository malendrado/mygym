package com.cortesdev.mygym.services.exception;

/** La suscripcion push que manda el navegador no es valida (direccion fuera de los servicios permitidos, claves mal formadas) — 400. */
public class InvalidPushSubscriptionException extends RuntimeException {

    public InvalidPushSubscriptionException(String message) {
        super(message);
    }
}
