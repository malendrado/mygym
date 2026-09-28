package com.cortesdev.mygym.services.exception;

public class NoActiveReservationException extends RuntimeException {

    public NoActiveReservationException() {
        super("No tienes ninguna reserva activa en este horario en este gimnasio");
    }
}
