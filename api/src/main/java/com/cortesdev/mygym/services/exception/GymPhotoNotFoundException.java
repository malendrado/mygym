package com.cortesdev.mygym.services.exception;

public class GymPhotoNotFoundException extends RuntimeException {

    public GymPhotoNotFoundException(Long gymId, Long photoId) {
        super("No se encontró la foto: gymId=" + gymId + ", photoId=" + photoId);
    }
}
