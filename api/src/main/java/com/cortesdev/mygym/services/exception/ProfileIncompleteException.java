package com.cortesdev.mygym.services.exception;

/** El socio todavía no completó RUT, dirección, fecha de nacimiento y celular — no puede reservar. */
public class ProfileIncompleteException extends RuntimeException {

    public ProfileIncompleteException() {
        super("Completa tus datos (RUT, dirección, fecha de nacimiento y celular) para reservar clases.");
    }
}
