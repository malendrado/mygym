package com.cortesdev.mygym.services.exception;

public class AdminRemovalNotAllowedException extends RuntimeException {

    public AdminRemovalNotAllowedException(String message) {
        super(message);
    }
}
