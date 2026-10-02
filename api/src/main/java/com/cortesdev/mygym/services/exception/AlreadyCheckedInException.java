package com.cortesdev.mygym.services.exception;

public class AlreadyCheckedInException extends RuntimeException {

    public AlreadyCheckedInException() {
        super("Ya marcaste tu asistencia a esta clase");
    }
}
