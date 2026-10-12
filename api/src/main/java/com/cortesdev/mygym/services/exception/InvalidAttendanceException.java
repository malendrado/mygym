package com.cortesdev.mygym.services.exception;

/** Marcado de asistencia o consulta de resumen inválidos (fuera de ventana, rango muy largo) — 400. */
public class InvalidAttendanceException extends RuntimeException {

    public InvalidAttendanceException(String message) {
        super(message);
    }
}
