package com.cortesdev.mygym.services.exception;

public class BrandingSuggestionException extends RuntimeException {

    public BrandingSuggestionException(String message) {
        super(message);
    }

    public BrandingSuggestionException(String message, Throwable cause) {
        super(message, cause);
    }
}
