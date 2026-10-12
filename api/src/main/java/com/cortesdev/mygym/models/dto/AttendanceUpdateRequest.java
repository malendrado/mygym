package com.cortesdev.mygym.models.dto;

import jakarta.validation.constraints.NotNull;

/** Marcar (true) o desmarcar (false) a mano la asistencia de una reserva. */
public record AttendanceUpdateRequest(@NotNull Boolean attended) {}
