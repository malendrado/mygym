package com.cortesdev.mygym.services.exception;

public class DuplicateMemberEmailException extends RuntimeException {

    private final String email;

    public DuplicateMemberEmailException(String email) {
        super("El email " + email + " ya tiene una cuenta en mygym (puede que ya sea socio de este u otro gimnasio, o administrador). Usa otro email.");
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
