package com.cortesdev.mygym.services.exception;

public class MemberNotFoundException extends RuntimeException {

    public MemberNotFoundException(Long id) {
        super("No se encontró el socio: id=" + id);
    }
}
