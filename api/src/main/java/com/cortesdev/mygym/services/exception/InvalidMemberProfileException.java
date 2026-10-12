package com.cortesdev.mygym.services.exception;

/** Algún dato de contacto del socio (RUT, dirección, fecha de nacimiento, celular) no es válido — 400. */
public class InvalidMemberProfileException extends RuntimeException {

    public InvalidMemberProfileException(String message) {
        super(message);
    }
}
