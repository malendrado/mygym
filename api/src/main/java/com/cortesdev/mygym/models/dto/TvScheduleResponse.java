package com.cortesdev.mygym.models.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** serverTime es la hora real del servidor (UTC) al momento de la respuesta — la TV la usa como
 *  referencia para no depender 100% del reloj propio del televisor, que puede estar mal
 *  configurado. nextDate es null si el gym no tiene ningún bloque activo. previous es la clase
 *  de HOY que terminó más recientemente (null si todavía no terminó ninguna). later son las
 *  clases de ese mismo día (nextDate) posteriores al horario de "next", para la agenda "más
 *  tarde". photos es la misma galería que ya ve el socio en /member (GymPhoto). */
public record TvScheduleResponse(
        String gymName,
        String logoSvg,
        String themeColor,
        String themeMode,
        String tagline,
        List<GymPhotoResponse> photos,
        Instant serverTime,
        List<TvBlockOccurrenceResponse> current,
        List<TvBlockOccurrenceResponse> next,
        LocalDate nextDate,
        TvBlockOccurrenceResponse previous,
        List<TvBlockOccurrenceResponse> later) {}
