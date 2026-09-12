package com.cortesdev.mygym.services.exception;

public class DuplicateOwnerEmailException extends RuntimeException {

    public DuplicateOwnerEmailException(String email) {
        super("Ya existe un usuario con este email: email=" + email);
    }
}
