package com.cortesdev.mygym.models;

public enum Role {
    SUPER_ADMIN,
    GYM_ADMIN,
    MEMBER,

    /** Acceso de solo-lectura a la demo comercial (ver DemoPreviewController) — nunca puede
     *  escribir nada, ver SecurityConfig. Vive en el mismo gym "demo" que GYM_ADMIN normalmente
     *  usaría, pero está bloqueado a nivel backend para cualquier método que no sea GET. */
    DEMO_ADMIN
}
