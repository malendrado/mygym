package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.ClassWaitlistEntry;
import com.cortesdev.mygym.models.dto.WaitlistStaffEntry;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.ClassWaitlistRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Lista de espera con nombre y celular para la Agenda del equipo (WaitlistService.waitingStaffByOccurrence). */
@ExtendWith(MockitoExtension.class)
class WaitlistStaffViewTest {

    private static final Long GYM_ID = 5L;
    private static final LocalDate FROM = LocalDate.of(2026, 10, 12);
    private static final LocalDate TO = LocalDate.of(2026, 10, 18);
    private static final LocalDate TUESDAY = LocalDate.of(2026, 10, 13);
    private static final Instant T0 = Instant.parse("2026-10-10T10:00:00Z");

    @Mock private ClassWaitlistRepository waitlistRepository;
    @Mock private GymBlockRepository gymBlockRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private GymRepository gymRepository;
    @Mock private MemberLifecycleEmailService emailService;
    @Mock private FixedWeekRule fixedWeekRule;
    @Mock private PushNotificationService pushService;
    @InjectMocks private WaitlistService waitlistService;

    private ClassWaitlistEntry entry(long id, long blockId, long memberId, LocalDate date, Instant createdAt) {
        return ClassWaitlistEntry.builder()
                .id(id)
                .gymBlockId(blockId)
                .memberId(memberId)
                .classDate(date)
                .createdAt(createdAt)
                .build();
    }

    private AppUser member(long id, String name, String phone, Long gymId) {
        return AppUser.builder().id(id).name(name).phone(phone).gymId(gymId).build();
    }

    @Test
    void ordersByArrivalWithPositionFromOneAndGroupsByOccurrence() {
        when(waitlistRepository.findByGymBlockIdInAndClassDateBetween(any(), any(), any()))
                .thenReturn(List.of(
                        entry(3, 1, 102, TUESDAY, T0.plusSeconds(60)),
                        entry(1, 1, 100, TUESDAY, T0),
                        entry(2, 2, 101, TUESDAY, T0),
                        entry(4, 1, 101, TUESDAY, T0.plusSeconds(30))));
        when(appUserRepository.findAllById(any()))
                .thenReturn(List.of(
                        member(100, "Ana", "+56911111111", GYM_ID),
                        member(101, "Pedro", null, GYM_ID),
                        member(102, "Luz", "  ", GYM_ID)));

        Map<String, List<WaitlistStaffEntry>> result =
                waitlistService.waitingStaffByOccurrence(GYM_ID, List.of(1L, 2L), FROM, TO);

        assertThat(result.get("1|" + TUESDAY))
                .containsExactly(
                        new WaitlistStaffEntry(100L, "Ana", "+56911111111", 1, T0),
                        new WaitlistStaffEntry(101L, "Pedro", null, 2, T0.plusSeconds(30)),
                        new WaitlistStaffEntry(102L, "Luz", null, 3, T0.plusSeconds(60)));
        assertThat(result.get("2|" + TUESDAY)).hasSize(1);
        verify(appUserRepository, times(1)).findAllById(any());
    }

    @Test
    void membersFromAnotherGymAreNeverExposed() {
        when(waitlistRepository.findByGymBlockIdInAndClassDateBetween(any(), any(), any()))
                .thenReturn(List.of(entry(1, 1, 100, TUESDAY, T0), entry(2, 1, 200, TUESDAY, T0.plusSeconds(5))));
        when(appUserRepository.findAllById(any()))
                .thenReturn(List.of(member(100, "Ana", "111", GYM_ID), member(200, "Intruso", "999", 99L)));

        List<WaitlistStaffEntry> list =
                waitlistService.waitingStaffByOccurrence(GYM_ID, List.of(1L), FROM, TO).get("1|" + TUESDAY);

        assertThat(list).extracting(WaitlistStaffEntry::name).containsExactly("Ana");
    }

    @Test
    void noBlocksOrNoEntriesSkipsTheMemberLookup() {
        assertThat(waitlistService.waitingStaffByOccurrence(GYM_ID, List.of(), FROM, TO)).isEmpty();

        when(waitlistRepository.findByGymBlockIdInAndClassDateBetween(any(), any(), any())).thenReturn(List.of());
        assertThat(waitlistService.waitingStaffByOccurrence(GYM_ID, List.of(1L), FROM, TO)).isEmpty();
        verify(appUserRepository, never()).findAllById(any());
    }
}
