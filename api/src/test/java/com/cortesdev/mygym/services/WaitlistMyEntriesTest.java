package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.dto.WaitlistEntryResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.ClassWaitlistRepository;
import com.cortesdev.mygym.repositories.ClassWaitlistRepository.WaitlistRow;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** "Mi lista de espera" (WaitlistService.myWaitlist): mapeo al DTO y exclusión de la clase de hoy que ya terminó. */
@ExtendWith(MockitoExtension.class)
class WaitlistMyEntriesTest {

    private static final Long GYM_ID = 5L;
    private static final Long MEMBER_ID = 3L;
    private static final ZoneId ZONE = ZoneId.of("America/Santiago");

    @Mock private ClassWaitlistRepository waitlistRepository;
    @Mock private GymBlockRepository gymBlockRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private GymRepository gymRepository;
    @Mock private MemberLifecycleEmailService emailService;
    @Mock private FixedWeekRule fixedWeekRule;
    @Mock private PushNotificationService pushService;
    @InjectMocks private WaitlistService waitlistService;

    @Test
    void mapsRowsWithTheirPlaceInLine() {
        LocalDate tomorrow = LocalDate.now(ZONE).plusDays(1);
        when(waitlistRepository.findMyUpcoming(any(), any(), any())).thenReturn(List.of(
                new WaitlistRow(9L, tomorrow, "Yoga", LocalTime.of(19, 0), LocalTime.of(20, 0), 1L),
                new WaitlistRow(10L, tomorrow.plusDays(1), "Spinning", LocalTime.of(7, 0), LocalTime.of(8, 0), 3L)));

        List<WaitlistEntryResponse> result = waitlistService.myWaitlist(GYM_ID, MEMBER_ID);

        assertThat(result).containsExactly(
                new WaitlistEntryResponse(9L, tomorrow, "Yoga", LocalTime.of(19, 0), LocalTime.of(20, 0), 1),
                new WaitlistEntryResponse(10L, tomorrow.plusDays(1), "Spinning", LocalTime.of(7, 0), LocalTime.of(8, 0), 3));
    }

    @Test
    void todaysClassThatAlreadyEndedIsExcludedButOneStillToCome() {
        LocalDate today = LocalDate.now(ZONE);
        // fin 00:00:00 de hoy: siempre ya pasó; fin 23:59:59.999: solo ha pasado si el test corre en el último segundo del día
        when(waitlistRepository.findMyUpcoming(any(), any(), any())).thenReturn(List.of(
                new WaitlistRow(9L, today, "Madrugada", LocalTime.MIN, LocalTime.MIN.plusSeconds(1), 1L),
                new WaitlistRow(10L, today, "Noche", LocalTime.of(23, 0), LocalTime.MAX, 2L)));

        List<WaitlistEntryResponse> result = waitlistService.myWaitlist(GYM_ID, MEMBER_ID);

        assertThat(result).extracting(WaitlistEntryResponse::label).containsExactly("Noche");
    }

    @Test
    void noWaitsGivesAnEmptyList() {
        when(waitlistRepository.findMyUpcoming(any(), any(), any())).thenReturn(List.of());

        assertThat(waitlistService.myWaitlist(GYM_ID, MEMBER_ID)).isEmpty();
    }
}
