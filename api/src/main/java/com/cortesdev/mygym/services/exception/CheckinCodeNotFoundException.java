package com.cortesdev.mygym.services.exception;

public class CheckinCodeNotFoundException extends RuntimeException {

    public CheckinCodeNotFoundException(String code) {
        super("Código de asistencia inválido o vencido: " + code);
    }
}
