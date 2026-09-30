package com.cortesdev.mygym.services.exception;

public class FlowNotConfiguredException extends RuntimeException {
    public FlowNotConfiguredException(Long gymId) {
        super("Este gimnasio todavía no tiene una cuenta de Flow configurada.");
    }
}
