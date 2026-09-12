package com.cortesdev.mygym.services.exception;

public class DuplicateSlugException extends RuntimeException {

    public DuplicateSlugException(String slug) {
        super("Ese slug de gimnasio ya está en uso: " + slug);
    }
}
