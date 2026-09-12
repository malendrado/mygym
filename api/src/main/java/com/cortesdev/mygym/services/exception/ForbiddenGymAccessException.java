package com.cortesdev.mygym.services.exception;

public class ForbiddenGymAccessException extends RuntimeException {

    public ForbiddenGymAccessException() {
        super("No tienes acceso a los datos de este gimnasio");
    }
}
