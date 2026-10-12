package com.cortesdev.mygym.services.exception;

public class CapacityExceededException extends RuntimeException {

    public CapacityExceededException(Long gymBlockId) {
        super("Esta clase se acaba de llenar. Puedes anotarte en la lista de espera.");
    }
}
