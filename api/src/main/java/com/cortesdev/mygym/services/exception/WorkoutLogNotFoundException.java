package com.cortesdev.mygym.services.exception;

public class WorkoutLogNotFoundException extends RuntimeException {

    public WorkoutLogNotFoundException(Long id) {
        super("No se encontró el registro de rutina: id=" + id);
    }
}
