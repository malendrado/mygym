package com.cortesdev.mygym.models.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/** Una espera vigente del socio ("Mi lista de espera"). position: lugar en la fila de esa clase (1 = primero). */
public record WaitlistEntryResponse(
        Long gymBlockId, LocalDate classDate, String label, LocalTime startTime, LocalTime endTime, Integer position) {}
