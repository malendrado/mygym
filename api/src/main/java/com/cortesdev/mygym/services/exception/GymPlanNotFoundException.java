package com.cortesdev.mygym.services.exception;

public class GymPlanNotFoundException extends RuntimeException {

    public GymPlanNotFoundException(Long gymId, Long planId) {
        super("No se encontró el plan: gymId=" + gymId + ", planId=" + planId);
    }
}
