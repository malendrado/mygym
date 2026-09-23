package com.cortesdev.mygym.services.exception;

/** El nombre escrito para confirmar la desvinculación no coincide con el nombre real del
 *  gimnasio — acción irreversible, no se ejecuta sin la confirmación exacta. */
public class GymNameMismatchException extends RuntimeException {

    public GymNameMismatchException() {
        super("El nombre no coincide — no se ejecutó la desvinculación.");
    }
}
