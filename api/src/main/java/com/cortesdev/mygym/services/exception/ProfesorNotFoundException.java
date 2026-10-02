package com.cortesdev.mygym.services.exception;

public class ProfesorNotFoundException extends RuntimeException {

    public ProfesorNotFoundException(Long gymId, Long userId) {
        super("No se encontró un profesor para este gimnasio: gymId=" + gymId + ", userId=" + userId);
    }
}
