package com.cortesdev.mygym.services.exception;

public class UnauthorizedGoogleLoginException extends RuntimeException {

    public UnauthorizedGoogleLoginException(String email) {
        super("No hay una cuenta registrada para este email de Google: email=" + email);
    }
}
