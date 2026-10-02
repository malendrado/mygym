package com.cortesdev.mygym.services.exception;

/** Reservación inválida para loguear (ya tiene log, no es del socio, no tiene check-in), día de
 *  plan que no pertenece al plan activo, o ni planDayId ni freeTextLabel — ver WorkoutService. */
public class InvalidWorkoutLogException extends RuntimeException {

    public InvalidWorkoutLogException(String message) {
        super(message);
    }
}
