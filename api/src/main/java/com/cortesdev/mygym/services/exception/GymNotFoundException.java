package com.cortesdev.mygym.services.exception;

public class GymNotFoundException extends RuntimeException {

    public GymNotFoundException(Long id) {
        super("No se encontró el gimnasio: id=" + id);
    }

    public GymNotFoundException(String slug) {
        super("No se encontró el gimnasio: slug=" + slug);
    }

    public GymNotFoundException(java.util.UUID publicId) {
        super("No se encontró el gimnasio: publicId=" + publicId);
    }
}
