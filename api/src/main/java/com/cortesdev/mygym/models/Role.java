package com.cortesdev.mygym.models;

public enum Role {
    SUPER_ADMIN,
    GYM_ADMIN,
    MEMBER,

    /** Acceso de solo-lectura a la demo comercial (ver DemoPreviewController) — nunca puede
     *  escribir nada, ver SecurityConfig. Vive en el mismo gym "demo" que GYM_ADMIN normalmente
     *  usaría, pero está bloqueado a nivel backend para cualquier método que no sea GET. */
    DEMO_ADMIN,

    /** Profesor de "Memoria Viva" (ver WorkoutService) — lo crea el GYM_ADMIN del gym. Lectura
     *  igual que GYM_ADMIN en todo /api/gym-admin/** (puede buscar socios en el roster), pero
     *  escritura solo en los endpoints de rutina/bitácora, ver SecurityConfig. Sin restricción de
     *  "mis alumnos": cualquier PROFESOR del gym ve y edita a cualquier socio, a propósito. */
    PROFESOR
}
