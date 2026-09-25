package com.cortesdev.mygym.models.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** attendees nunca manda email ni apellido (misma regla de privacidad que "Ver quién reservó"),
 *  ver TvAttendeeResponse. taken puede ser mayor a attendees.size() nunca; se manda aparte solo
 *  por claridad en el frontend (ya es attendees.size(), pero evita que la TV tenga que
 *  calcularlo). instructorPhoto es el mismo campo que ya carga gym-admin (data URI, nullable). */
public record TvBlockOccurrenceResponse(
        Long blockId,
        String label,
        String category,
        String instructorName,
        String instructorPhoto,
        LocalTime startTime,
        LocalTime endTime,
        LocalDate classDate,
        Integer capacity,
        int taken,
        List<TvAttendeeResponse> attendees) {}
