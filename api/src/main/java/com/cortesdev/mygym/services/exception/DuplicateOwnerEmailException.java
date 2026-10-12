package com.cortesdev.mygym.services.exception;

public class DuplicateOwnerEmailException extends RuntimeException {

    public DuplicateOwnerEmailException(String email) {
        super("El email " + email + " ya tiene una cuenta en mygym (por ejemplo, ya es administrador, profesor o socio de un gimnasio). Usa otro email.");
    }
}
