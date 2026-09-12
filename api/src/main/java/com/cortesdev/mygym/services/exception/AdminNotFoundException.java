package com.cortesdev.mygym.services.exception;

public class AdminNotFoundException extends RuntimeException {

    public AdminNotFoundException(Long gymId, Long userId) {
        super("No se encontró un administrador para este gimnasio: gymId=" + gymId + ", userId=" + userId);
    }
}
