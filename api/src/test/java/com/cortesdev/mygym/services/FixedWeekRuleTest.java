package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.repositories.MemberFixedClassRepository;
import com.cortesdev.mygym.services.exception.BookingWindowClosedException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Socios con clases fijas: solo reservan y cancelan en la semana en curso, lunes a domingo (Mockito, sin DB). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FixedWeekRuleTest {

    private static final Long MEMBER_ID = 8L;

    @Mock private MemberFixedClassRepository memberFixedClassRepository;
    @InjectMocks private FixedWeekRule rule;

    private final LocalDate today = LocalDate.now(ZoneId.of("America/Santiago"));
    private final LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    private final LocalDate sunday = monday.plusDays(6);

    private Gym gym(boolean on) {
        return Gym.builder().id(4L).fixedMembersCurrentWeekOnly(on).build();
    }

    @Test
    void conElInterruptorApagadoNoHacePreguntasNiRestringe() {
        assertThatCode(() -> rule.assertAllowed(gym(false), MEMBER_ID, sunday.plusDays(10), "reservar"))
                .doesNotThrowAnyException();
        verify(memberFixedClassRepository, never()).existsByMemberId(MEMBER_ID);
    }

    @Test
    void sinClasesFijasNoSeRestringe() {
        when(memberFixedClassRepository.existsByMemberId(MEMBER_ID)).thenReturn(false);

        assertThatCode(() -> rule.assertAllowed(gym(true), MEMBER_ID, sunday.plusDays(3), "reservar"))
                .doesNotThrowAnyException();
    }

    @Test
    void conFijasPuedeTocarCualquierDiaDeLaSemanaEnCurso() {
        when(memberFixedClassRepository.existsByMemberId(MEMBER_ID)).thenReturn(true);

        assertThatCode(() -> rule.assertAllowed(gym(true), MEMBER_ID, monday, "reservar")).doesNotThrowAnyException();
        assertThatCode(() -> rule.assertAllowed(gym(true), MEMBER_ID, sunday, "cancelar")).doesNotThrowAnyException();
        verify(memberFixedClassRepository, never()).existsByMemberId(null);
    }

    @Test
    void conFijasLaSemanaSiguienteSeRechazaDesdeElLunes() {
        when(memberFixedClassRepository.existsByMemberId(MEMBER_ID)).thenReturn(true);

        assertThatThrownBy(() -> rule.assertAllowed(gym(true), MEMBER_ID, sunday.plusDays(1), "reservar"))
                .isInstanceOf(BookingWindowClosedException.class)
                .hasMessageContaining("solo puedes reservar clases de esta semana")
                .hasMessageContaining("domingo");
    }

    @Test
    void cancelarUnaClaseDeLaSemanaSiguienteTambienSeRechaza() {
        when(memberFixedClassRepository.existsByMemberId(MEMBER_ID)).thenReturn(true);

        assertThatThrownBy(() -> rule.assertAllowed(gym(true), MEMBER_ID, sunday.plusDays(2), "cancelar"))
                .isInstanceOf(BookingWindowClosedException.class)
                .hasMessageContaining("solo puedes cancelar clases de esta semana");
    }

    @Test
    void laSemanaVaDeLunesADomingo() {
        assertThat(monday.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(sunday.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(today).isBetween(monday, sunday);
    }
}
