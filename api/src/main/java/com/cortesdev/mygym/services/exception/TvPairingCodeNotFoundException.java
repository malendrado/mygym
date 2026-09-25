package com.cortesdev.mygym.services.exception;

public class TvPairingCodeNotFoundException extends RuntimeException {

    public TvPairingCodeNotFoundException(String code) {
        super("Código de emparejamiento inválido o vencido: " + code);
    }
}
