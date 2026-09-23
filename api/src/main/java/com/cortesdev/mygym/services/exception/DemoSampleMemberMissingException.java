package com.cortesdev.mygym.services.exception;

/** El gym demo no tiene sembrado su socio de muestra (ver DemoPreviewController) — nunca debería
 *  pasar en producción, solo indica que el seed de la demo no corrió o se borró por error. */
public class DemoSampleMemberMissingException extends RuntimeException {

    public DemoSampleMemberMissingException(Long gymId) {
        super("El gym " + gymId + " no tiene un socio de muestra sembrado para la demo");
    }
}
