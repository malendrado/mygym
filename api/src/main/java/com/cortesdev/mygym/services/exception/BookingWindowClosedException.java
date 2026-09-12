package com.cortesdev.mygym.services.exception;

public class BookingWindowClosedException extends RuntimeException {

    public BookingWindowClosedException(String message) {
        super(message);
    }
}
