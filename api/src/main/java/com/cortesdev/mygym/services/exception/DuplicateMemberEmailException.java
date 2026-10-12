package com.cortesdev.mygym.services.exception;

public class DuplicateMemberEmailException extends RuntimeException {

    public DuplicateMemberEmailException(String email) {
        super("El email " + email + " ya tiene una cuenta en mygym (puede que ya sea socio de este u otro gimnasio, o administrador). Usa otro email.");
    }
}
