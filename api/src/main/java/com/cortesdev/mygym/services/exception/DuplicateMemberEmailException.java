package com.cortesdev.mygym.services.exception;

public class DuplicateMemberEmailException extends RuntimeException {

    public DuplicateMemberEmailException(String email) {
        super("Ya existe un socio con este email: email=" + email);
    }
}
