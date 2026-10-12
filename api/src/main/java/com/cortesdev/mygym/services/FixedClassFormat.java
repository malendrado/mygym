package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.GymBlock;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Textos de las clases fijas (resumen de la lista de Socios, emails, motivos) en un solo lugar. */
final class FixedClassFormat {

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    private FixedClassFormat() {}

    static String dayShort(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "Lun";
            case TUESDAY -> "Mar";
            case WEDNESDAY -> "Mié";
            case THURSDAY -> "Jue";
            case FRIDAY -> "Vie";
            case SATURDAY -> "Sáb";
            case SUNDAY -> "Dom";
        };
    }

    static String dayLong(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "lunes";
            case TUESDAY -> "martes";
            case WEDNESDAY -> "miércoles";
            case THURSDAY -> "jueves";
            case FRIDAY -> "viernes";
            case SATURDAY -> "sábado";
            case SUNDAY -> "domingo";
        };
    }

    /** "Lun 19:00 GAP" */
    static String describeShort(GymBlock block) {
        return dayShort(block.getDayOfWeek()) + " " + block.getStartTime().format(HOUR) + " " + block.getLabel();
    }

    /** "lunes 19:00 (GAP)" — para frases dentro de un email. */
    static String describeLong(GymBlock block) {
        return dayLong(block.getDayOfWeek()) + " " + block.getStartTime().format(HOUR) + " (" + block.getLabel() + ")";
    }

    /** Resumen ordenado por día de la semana y hora: "Lun 19:00 GAP · Mié 19:00 GAP". Null si no hay clases. */
    static String summary(List<GymBlock> blocks) {
        if (blocks.isEmpty()) {
            return null;
        }
        return blocks.stream()
                .sorted(Comparator.comparing(GymBlock::getDayOfWeek).thenComparing(GymBlock::getStartTime))
                .map(FixedClassFormat::describeShort)
                .collect(Collectors.joining(" · "));
    }
}
