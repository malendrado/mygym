package com.cortesdev.mygym.services.exception;

public class TooManyGymPhotosException extends RuntimeException {

    public TooManyGymPhotosException(int max) {
        super("Ya alcanzaste el máximo de " + max + " fotos");
    }
}
