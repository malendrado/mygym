package com.cortesdev.mygym.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.TvScreen;
import com.cortesdev.mygym.models.dto.MemberCreateRequest;
import com.cortesdev.mygym.models.dto.MemberImportRow;
import com.cortesdev.mygym.models.dto.MemberImportRowResult;
import com.cortesdev.mygym.models.dto.TvScheduleResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymPlanRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.TvScreenRepository;
import com.cortesdev.mygym.services.exception.GymLimitReachedException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Los limites y la pausa realmente se APLICAN en cada camino: alta manual, Excel, profesores, administradores y la TV.
 * (Antes el cupo de socios se guardaba pero ningun flujo lo comparaba.)
 */
@ExtendWith(MockitoExtension.class)
class GymLimitsEnforcementTest {

    private static final Long GYM_ID = 5L;

    // ---- socios: alta manual y Excel ----

    @Mock private AppUserRepository appUserRepository;
    @Mock private GymPlanRepository gymPlanRepository;
    @Mock private GymRepository gymRepository;
    @Mock private MemberImportRowService memberImportRowService;
    @Mock private GymCapacityService gymCapacityService;
    @InjectMocks private MemberService memberService;

    private final Gym gym = Gym.builder().id(GYM_ID).name("XaloGym").maxUsers(10).active(true).build();

    private MemberImportRow row(String email) {
        return new MemberImportRow("Nombre", email, null, null, null, null, null, null, null, null);
    }

    @Test
    void manualMemberCreationStopsWhenThereIsNoRoomAndSavesNothing() {
        when(appUserRepository.existsByEmail("ana@x.cl")).thenReturn(false);
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(gym));
        doThrow(new GymLimitReachedException(GymLimitReachedException.Kind.MEMBERS, "límite"))
                .when(gymCapacityService).assertMemberCapacity(gym, 1);

        assertThrows(
                GymLimitReachedException.class,
                () -> memberService.createMember(GYM_ID, new MemberCreateRequest("Ana", "ana@x.cl")));

        verify(appUserRepository, never()).save(any(AppUser.class));
    }

    @Test
    void theExcelImportOnlyLoadsAsManyRowsAsThereAreFreeSlotsAndExplainsTheRest() {
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(gym));
        when(gymPlanRepository.findByGymId(GYM_ID)).thenReturn(List.of());
        when(gymCapacityService.freeMemberSlots(gym)).thenReturn(2);
        when(memberImportRowService.importRow(eq(GYM_ID), eq(gym), any(), any(), any()))
                .thenAnswer(inv -> new MemberImportRowResult(((MemberImportRow) inv.getArgument(2)).email(), true, null));

        List<MemberImportRowResult> results = memberService.importMembers(
                GYM_ID, List.of(row("a@x.cl"), row("b@x.cl"), row("c@x.cl"), row("d@x.cl")));

        assertEquals(List.of(true, true, false, false), results.stream().map(MemberImportRowResult::success).toList());
        assertTrue(results.get(2).error().startsWith("Sin cupo"), results.get(2).error());
        assertTrue(results.get(3).error().contains("límite de 10 socios"), results.get(3).error());
        verify(memberImportRowService, org.mockito.Mockito.times(2)).importRow(any(), any(), any(), any(), any());
    }

    @Test
    void aDuplicateOrInvalidRowDoesNotConsumeASlot() {
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(gym));
        when(gymPlanRepository.findByGymId(GYM_ID)).thenReturn(List.of());
        when(gymCapacityService.freeMemberSlots(gym)).thenReturn(1);
        when(memberImportRowService.importRow(eq(GYM_ID), eq(gym), any(), any(), any()))
                .thenAnswer(inv -> {
                    String email = ((MemberImportRow) inv.getArgument(2)).email();
                    return email.startsWith("dup")
                            ? new MemberImportRowResult(email, false, "Ya existe un socio con ese email.")
                            : new MemberImportRowResult(email, true, null);
                });

        List<MemberImportRowResult> results =
                memberService.importMembers(GYM_ID, List.of(row("dup@x.cl"), row("nuevo@x.cl"), row("otro@x.cl")));

        assertEquals(List.of(false, true, false), results.stream().map(MemberImportRowResult::success).toList());
        assertEquals("Ya existe un socio con ese email.", results.get(0).error());
        assertTrue(results.get(2).error().startsWith("Sin cupo"));
    }

    // ---- TV en pausa ----

    @Mock private TvScreenRepository tvScreenRepository;
    @Mock private GymBlockRepository gymBlockRepository;
    @Mock private com.cortesdev.mygym.repositories.TvPairingCodeRepository pairingCodeRepository;
    @Mock private com.cortesdev.mygym.repositories.GymPhotoRepository gymPhotoRepository;
    @Mock private com.cortesdev.mygym.repositories.TvCheckinCodeRepository checkinCodeRepository;
    @Mock private GymClosureService gymClosureService;
    @Mock private ReservationService reservationService;
    @InjectMocks private TvScreenService tvScreenService;

    private TvScreen screen() {
        return TvScreen.builder().id(1L).gymId(GYM_ID).token("tok").createdAt(Instant.now()).build();
    }

    @Test
    void aPausedGymTvGetsOnlyTheFrameAndThePausedFlag_neverClassesOrMemberNames() {
        when(tvScreenRepository.findByToken("tok")).thenReturn(Optional.of(screen()));
        when(gymRepository.findById(GYM_ID))
                .thenReturn(Optional.of(Gym.builder().id(GYM_ID).name("XaloGym").themeColor("#2fd0ff").themeMode("DARK").active(false).build()));

        TvScheduleResponse schedule = tvScreenService.getSchedule("tok");

        assertTrue(schedule.paused());
        assertEquals("XaloGym", schedule.gymName());
        assertTrue(schedule.current().isEmpty());
        assertTrue(schedule.next().isEmpty());
        assertTrue(schedule.later().isEmpty());
        assertNull(schedule.previous());
        verify(gymBlockRepository, never()).findByGymId(any());
        verify(reservationService, never()).getOccurrenceAttendeesForRange(any(), any(), any());
    }

    @Test
    void anActiveGymTvIsNotMarkedAsPaused() {
        when(tvScreenRepository.findByToken("tok")).thenReturn(Optional.of(screen()));
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(gym));
        when(gymBlockRepository.findByGymId(GYM_ID)).thenReturn(List.of());

        TvScheduleResponse schedule = tvScreenService.getSchedule("tok");

        assertFalse(schedule.paused());
    }

    @Test
    void aPausedGymTvGetsNoPhotos() {
        when(tvScreenRepository.findByToken("tok")).thenReturn(Optional.of(screen()));
        when(gymRepository.existsByIdAndActiveTrue(GYM_ID)).thenReturn(false);

        assertTrue(tvScreenService.getPhotos("tok").isEmpty());
        verify(gymPhotoRepository, never()).findByGymIdOrderBySortOrderAsc(any());
    }
}
