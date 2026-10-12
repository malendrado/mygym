package com.cortesdev.mygym.services.exception;

public class FlowNotConfiguredException extends RuntimeException {
    public FlowNotConfiguredException(Long gymId) {
        super("Este gimnasio todavía no tiene el pago online activado. Paga por transferencia o consulta directamente con el gimnasio.");
    }
}
