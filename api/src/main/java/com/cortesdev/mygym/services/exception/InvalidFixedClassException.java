package com.cortesdev.mygym.services.exception;

/** Pedido de clases fijas inválido (dos clases el mismo día, bloque inactivo, etc.) — 400. */
public class InvalidFixedClassException extends RuntimeException {

    public InvalidFixedClassException(String message) {
        super(message);
    }
}
