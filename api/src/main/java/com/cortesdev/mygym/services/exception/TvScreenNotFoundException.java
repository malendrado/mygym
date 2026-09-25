package com.cortesdev.mygym.services.exception;

public class TvScreenNotFoundException extends RuntimeException {

    public TvScreenNotFoundException(Object identifier) {
        super("No se encontró la pantalla de TV: " + identifier);
    }
}
