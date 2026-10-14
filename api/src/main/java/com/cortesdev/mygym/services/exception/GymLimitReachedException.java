package com.cortesdev.mygym.services.exception;

/**
 * Se llego al limite de socios, profesores o administradores de un gimnasio — 409. `code` deja que la app distinga el caso
 * (ej. mostrar "avisa a tu gimnasio" en la pagina de alta) sin depender del texto del mensaje.
 */
public class GymLimitReachedException extends RuntimeException {

    public enum Kind {
        MEMBERS,
        PROFESORES,
        ADMINS
    }

    private final Kind kind;

    public GymLimitReachedException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
