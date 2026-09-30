package com.cortesdev.mygym.services.exception;

public class InvalidRutException extends RuntimeException {
    public InvalidRutException(String field) {
        super("El " + field + " no es un RUT chileno válido.");
    }
}
