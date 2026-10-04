package com.cortesdev.mygym.services.exception;

public class GymClosedException extends RuntimeException {

    public GymClosedException(String reason) {
        super(reason != null && !reason.isBlank()
                ? "El gimnasio está cerrado en esta fecha: " + reason
                : "El gimnasio está cerrado en esta fecha.");
    }
}
