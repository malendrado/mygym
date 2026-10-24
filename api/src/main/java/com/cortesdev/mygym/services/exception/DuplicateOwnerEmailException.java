package com.cortesdev.mygym.services.exception;

public class DuplicateOwnerEmailException extends RuntimeException {

    private final String email;
    /** true = mensaje genérico (no dice dónde vive la cuenta); false = ya explica algo concreto de ESTE gimnasio. */
    private final boolean generic;

    public DuplicateOwnerEmailException(String email) {
        super("El email " + email + " ya tiene una cuenta en mygym (por ejemplo, ya es administrador, profesor o socio de un gimnasio). Usa otro email.");
        this.email = email;
        this.generic = true;
    }

    private DuplicateOwnerEmailException(String email, String message) {
        super(message);
        this.email = email;
        this.generic = false;
    }

    public String getEmail() {
        return email;
    }

    public boolean isGeneric() {
        return generic;
    }

    /** Se intenta agregar como profesor a quien ya administra ESTE gimnasio: un administrador ya puede hacer todo lo que
     *  hace un profesor, así que no hace falta (y un email solo puede tener una cuenta). */
    public static DuplicateOwnerEmailException alreadyAdminOfThisGym(String email) {
        return new DuplicateOwnerEmailException(
                email,
                "El email " + email + " ya es administrador de tu gimnasio. Un administrador ya puede hacer todo lo que hace un profesor"
                        + " (rutinas, asistencia, clases fijas), así que no necesitas agregarlo como profesor.");
    }

    /** Se intenta agregar como administrador a quien ya es profesor de ESTE gimnasio. */
    public static DuplicateOwnerEmailException alreadyProfesorOfThisGym(String email) {
        return new DuplicateOwnerEmailException(
                email,
                "El email " + email + " ya es profesor de tu gimnasio. Para agregarlo como administrador, primero quítalo de los profesores.");
    }
}
