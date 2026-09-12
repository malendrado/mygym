package com.cortesdev.mygym.services.exception;

public class GymBlockNotFoundException extends RuntimeException {

    public GymBlockNotFoundException(Long gymId, Long blockId) {
        super("No se encontró el bloque: gymId=" + gymId + ", blockId=" + blockId);
    }
}
