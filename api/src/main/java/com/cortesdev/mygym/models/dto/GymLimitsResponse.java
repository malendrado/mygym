package com.cortesdev.mygym.models.dto;

/**
 * Cuántos cupos usa un gimnasio y cuántos le quedan, para mostrarlo en los paneles (admin del gimnasio y super-admin).
 * Socios: cupo configurable por el super-admin. Profesores y administradores: topes fijos de la plataforma.
 */
public record GymLimitsResponse(Usage members, Usage profesores, Usage admins) {

    /** `free` nunca es negativo (si se bajó el cupo por debajo de lo usado, queda en 0 y `used` > `max`). */
    public record Usage(int used, int max, int free) {
        public static Usage of(long used, int max) {
            return new Usage((int) used, max, (int) Math.max(0, max - used));
        }
    }
}
