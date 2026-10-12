package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.FixedClassImportMemberResult;
import com.cortesdev.mygym.models.dto.FixedClassImportRow;
import com.cortesdev.mygym.models.dto.FixedClassRowResult;
import com.cortesdev.mygym.models.dto.FixedClassesSaveResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.MemberFixedClassRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Unidades puras (Mockito, sin DB): validación del Excel de clases fijas (día + hora + NOMBRE). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FixedClassImportServiceTest {

    private static final Long GYM_ID = 2L;

    @Mock private AppUserRepository appUserRepository;
    @Mock private GymBlockRepository gymBlockRepository;
    @Mock private MemberFixedClassRepository fixedClassRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private MemberService memberService;
    @Mock private FixedClassService fixedClassService;

    @InjectMocks private FixedClassImportService service;

    private final AppUser camila = AppUser.builder().id(4L).gymId(GYM_ID).role(Role.MEMBER).email("camila@x.cl").name("Camila").build();
    private final AppUser tomas = AppUser.builder().id(5L).gymId(GYM_ID).role(Role.MEMBER).email("tomas@x.cl").name("Tomás").build();
    // Dos clases EXACTAMENTE a la misma hora el lunes: el caso que el nombre tiene que resolver.
    private final GymBlock gapMonday = block(1L, "GAP", DayOfWeek.MONDAY, 19);
    private final GymBlock crossfitMonday = block(2L, "CrossFit", DayOfWeek.MONDAY, 19);
    private final GymBlock gapWednesday = block(3L, "GAP", DayOfWeek.WEDNESDAY, 19);

    private static GymBlock block(long id, String label, DayOfWeek day, int hour) {
        return GymBlock.builder().id(id).gymId(GYM_ID).label(label).dayOfWeek(day)
                .startTime(LocalTime.of(hour, 0)).endTime(LocalTime.of(hour + 1, 0)).capacity(10).active(true).build();
    }

    private static FixedClassImportRow row(String email, DayOfWeek day, int hour, String className) {
        return new FixedClassImportRow(email, day, LocalTime.of(hour, 0), className);
    }

    @BeforeEach
    void setUp() {
        when(appUserRepository.findByGymIdAndRole(GYM_ID, Role.MEMBER)).thenReturn(List.of(camila, tomas));
        when(gymBlockRepository.findByGymId(GYM_ID)).thenReturn(List.of(gapMonday, crossfitMonday, gapWednesday));
        when(memberService.hasActiveMembership(any())).thenReturn(true);
    }

    @Test
    void theNameDecidesBetweenTwoClassesAtTheSameTimeIgnoringCaseAndAccents() {
        List<FixedClassRowResult> results = service.validate(GYM_ID, List.of(
                row("camila@x.cl", DayOfWeek.MONDAY, 19, "gap"),
                row("tomas@x.cl", DayOfWeek.MONDAY, 19, "  CROSSFIT ")));

        assertThat(results).extracting(FixedClassRowResult::status).containsExactly("OK", "OK");
    }

    @Test
    void aMissingNameIsAnErrorEvenWhenOnlyOneClassExistsAtThatTime() {
        List<FixedClassRowResult> results = service.validate(GYM_ID, List.of(
                row("camila@x.cl", DayOfWeek.MONDAY, 19, ""),
                row("tomas@x.cl", DayOfWeek.WEDNESDAY, 19, " ")));

        assertThat(results).extracting(FixedClassRowResult::status).containsExactly("ERROR", "ERROR");
        assertThat(results.get(0).message()).contains("Falta el nombre de la clase");
    }

    @Test
    void aWrongNameListsWhatDoesExistAtThatTimeAndNeverPicksTheOnlyOne() {
        List<FixedClassRowResult> results = service.validate(GYM_ID, List.of(
                row("camila@x.cl", DayOfWeek.WEDNESDAY, 19, "Yoga"),
                row("tomas@x.cl", DayOfWeek.FRIDAY, 7, "GAP")));

        assertThat(results.get(0).status()).isEqualTo("ERROR");
        assertThat(results.get(0).message()).contains("No hay una clase 'Yoga'").contains("A esa hora hay: GAP");
        assertThat(results.get(1).message()).startsWith("No hay clases el viernes a las 07:00");
    }

    @Test
    void unknownEmailIsAnError() {
        List<FixedClassRowResult> results = service.validate(GYM_ID, List.of(row("nadie@x.cl", DayOfWeek.MONDAY, 19, "GAP")));

        assertThat(results.get(0).status()).isEqualTo("ERROR");
        assertThat(results.get(0).message()).contains("No hay un socio con ese email");
    }

    @Test
    void twoClassesTheSameDayForOneMemberIsAnErrorAndOmitsTheWholeMember() {
        List<FixedClassRowResult> results = service.validate(GYM_ID, List.of(
                row("camila@x.cl", DayOfWeek.WEDNESDAY, 19, "GAP"),
                row("camila@x.cl", DayOfWeek.MONDAY, 19, "GAP"),
                row("camila@x.cl", DayOfWeek.MONDAY, 19, "CrossFit"),
                row("tomas@x.cl", DayOfWeek.MONDAY, 19, "CrossFit")));

        assertThat(results).extracting(FixedClassRowResult::status).containsExactly("OMITTED", "OMITTED", "ERROR", "OK");
        assertThat(results.get(2).message()).contains("Solo puede tener una clase fija por día");
    }

    @Test
    void anExactRepeatedRowIsAnError() {
        List<FixedClassRowResult> results = service.validate(GYM_ID, List.of(
                row("camila@x.cl", DayOfWeek.MONDAY, 19, "GAP"),
                row("camila@x.cl", DayOfWeek.MONDAY, 19, "gap")));

        assertThat(results.get(1).status()).isEqualTo("ERROR");
        assertThat(results.get(1).message()).startsWith("Fila repetida");
    }

    @Test
    void memberWithoutAnActivePlanGetsAWarningNotAnError() {
        when(memberService.hasActiveMembership(camila)).thenReturn(false);

        List<FixedClassRowResult> results = service.validate(GYM_ID, List.of(row("camila@x.cl", DayOfWeek.MONDAY, 19, "GAP")));

        assertThat(results.get(0).status()).isEqualTo("WARNING");
        assertThat(results.get(0).message()).contains("No tiene plan activo");
    }

    @Test
    void validateNeverWritesAnything() {
        service.validate(GYM_ID, List.of(row("camila@x.cl", DayOfWeek.MONDAY, 19, "GAP")));

        verifyNoInteractions(fixedClassService);
    }

    @Test
    void importLoadsEachCleanMemberAndSkipsTheOnesWithErrors() {
        when(fixedClassService.replaceForMember(eq(GYM_ID), eq(5L), eq(List.of(2L)), eq(9L)))
                .thenReturn(new FixedClassesSaveResponse(List.of(), 4, List.of(), 0, false, false));

        List<FixedClassImportMemberResult> results = service.importRows(GYM_ID, List.of(
                row("camila@x.cl", DayOfWeek.MONDAY, 19, "Yoga"),
                row("tomas@x.cl", DayOfWeek.MONDAY, 19, "CrossFit")), 9L);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).ok()).isFalse();
        assertThat(results.get(0).message()).contains("No hay una clase 'Yoga'");
        assertThat(results.get(1).ok()).isTrue();
        assertThat(results.get(1).created()).isEqualTo(4);
        verify(fixedClassService, never()).replaceForMember(eq(GYM_ID), eq(4L), any(), any());
    }
}
