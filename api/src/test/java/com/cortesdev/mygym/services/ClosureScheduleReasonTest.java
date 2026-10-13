package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Unidad pura: el motivo de un cierre se resuelve con lo que ya trae el ClosureSchedule, sin consultar la base. */
class ClosureScheduleReasonTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 13);

    @Test
    void wholeDayReasonAppliesToAnyBlockThatDay() {
        var schedule = new GymClosureService.ClosureSchedule(Set.of(DAY), Set.of(), Map.of(DAY, "Corte de luz"), Map.of());

        assertThat(schedule.reasonFor(1L, DAY)).isEqualTo("Corte de luz");
        assertThat(schedule.reasonFor(2L, DAY)).isEqualTo("Corte de luz");
    }

    @Test
    void blockSpecificReasonOnlyAppliesToThatBlockAndWinsOverTheDayOne() {
        var schedule = new GymClosureService.ClosureSchedule(
                Set.of(DAY), Set.of("2|" + DAY), Map.of(DAY, "Feriado"), Map.of("2|" + DAY, "Mantención de sala"));

        assertThat(schedule.reasonFor(2L, DAY)).isEqualTo("Mantención de sala");
        assertThat(schedule.reasonFor(1L, DAY)).isEqualTo("Feriado");
    }

    @Test
    void noClosureMeansNoReason() {
        var schedule = new GymClosureService.ClosureSchedule(Set.of(), Set.of());

        assertThat(schedule.reasonFor(1L, DAY)).isNull();
        assertThat(schedule.isClosed(1L, DAY)).isFalse();
    }
}
