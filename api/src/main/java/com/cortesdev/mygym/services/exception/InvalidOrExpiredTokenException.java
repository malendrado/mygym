package com.cortesdev.mygym.services.exception;

public class InvalidOrExpiredTokenException extends RuntimeException {

    public InvalidOrExpiredTokenException() {
        super("Este link ya no es válido — puede que haya expirado o que ya lo hayas usado.");
    }
}
