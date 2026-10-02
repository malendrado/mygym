package com.cortesdev.mygym.services.exception;

public class WorkoutPlanNotFoundException extends RuntimeException {

    public WorkoutPlanNotFoundException(Long memberId) {
        super("Este socio no tiene una rutina activa: memberId=" + memberId);
    }
}
