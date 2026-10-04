package com.cortesdev.mygym.services.exception;

public class GymClosureNotFoundException extends RuntimeException {

    public GymClosureNotFoundException(Long closureId) {
        super("No se encontró el cierre: id=" + closureId);
    }
}
