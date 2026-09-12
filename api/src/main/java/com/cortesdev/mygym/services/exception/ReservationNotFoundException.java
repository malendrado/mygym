package com.cortesdev.mygym.services.exception;

public class ReservationNotFoundException extends RuntimeException {

    public ReservationNotFoundException(Long id) {
        super("No se encontró la reserva: id=" + id);
    }
}
