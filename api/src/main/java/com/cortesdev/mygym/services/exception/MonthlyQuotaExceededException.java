package com.cortesdev.mygym.services.exception;

public class MonthlyQuotaExceededException extends RuntimeException {

    public MonthlyQuotaExceededException() {
        super("Ya usaste todas tus clases de este mes según tu plan");
    }
}
