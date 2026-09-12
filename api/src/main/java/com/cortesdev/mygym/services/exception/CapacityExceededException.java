package com.cortesdev.mygym.services.exception;

public class CapacityExceededException extends RuntimeException {

    public CapacityExceededException(Long gymBlockId) {
        super("No quedan cupos para esta clase: gymBlockId=" + gymBlockId);
    }
}
