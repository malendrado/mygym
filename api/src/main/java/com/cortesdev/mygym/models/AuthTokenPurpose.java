package com.cortesdev.mygym.models;

public enum AuthTokenPurpose {
    /** Admin agregó un socio (createMember/import) — app_user_id ya existe, solo falta la contraseña. */
    INVITE,
    /** Alta pública en /j/{slug} sin Google — app_user_id queda null hasta confirmar el token. */
    SELF_REGISTER,
    /** "Olvidé mi contraseña" — app_user_id ya existe, se sobreescribe su passwordHash. */
    PASSWORD_RESET
}
