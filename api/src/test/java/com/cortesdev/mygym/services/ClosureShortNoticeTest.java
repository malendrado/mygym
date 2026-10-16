package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymClosure;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymClosureRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Cierres de ultima hora (de todo el dia, empiezan hoy o manana): se avisa a TODOS los socios, al crearlos y al levantarlos.
 * Los programados con 2 o mas dias de anticipacion, o de clases puntuales, avisan solo a los afectados.
 */
@ExtendWith(MockitoExtension.class)
class ClosureShortNoticeTest {

    private static final Long GYM_ID = 5L;
    private static final Long CLOSURE_ID = 11L;
    private static final ZoneId ZONE = ZoneId.of("America/Santiago");
    private static final LocalDate TODAY = LocalDate.now(ZONE);

    @Mock private GymClosureRepository gymClosureRepository;
    @Mock private GymRepository gymRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private com.cortesdev.mygym.repositories.GymBlockRepository gymBlockRepository;
    @Mock private com.cortesdev.mygym.repositories.GymPlanRepository gymPlanRepository;
    @Mock private MemberLifecycleEmailService emailService;
    @Mock private PushNotificationService pushService;
    @InjectMocks private ClosureNotificationService closureNotificationService;
    @InjectMocks private MemberChangeNotifier memberChangeNotifier;

    private final Gym gym = Gym.builder().id(GYM_ID).name("XaloGym").active(true).build();

    private GymClosure closure(int startOffset, int endOffset, boolean wholeDays) {
        return GymClosure.builder()
                .id(CLOSURE_ID).gymId(GYM_ID).wholeDays(wholeDays).reason("corte de luz")
                .startDate(TODAY.plusDays(startOffset)).endDate(TODAY.plusDays(endOffset))
                .createdAt(Instant.now()).build();
    }

    private AppUser member(long id, boolean active) {
        return AppUser.builder().id(id).gymId(GYM_ID).role(Role.MEMBER).active(active).email("m" + id + "@x.cl").name("Socio " + id).build();
    }

    // ---- la regla ----

    @Test
    void theRuleIsWholeDayClosuresStartingTodayOrTomorrow() {
        assertThat(closure(0, 0, true).wasCreatedWithShortNotice()).isTrue();
        assertThat(closure(1, 1, true).wasCreatedWithShortNotice()).isTrue();
        assertThat(closure(1, 4, true).wasCreatedWithShortNotice()).isTrue();
        assertThat(closure(2, 2, true).wasCreatedWithShortNotice()).isFalse();
        assertThat(closure(10, 12, true).wasCreatedWithShortNotice()).isFalse();
        assertThat(closure(0, 0, false).wasCreatedWithShortNotice()).isFalse(); // clases puntuales: solo afectados
    }

    @Test
    void aClosureWithoutDatesNeverCountsAsShortNotice() {
        assertThat(GymClosure.builder().wholeDays(true).build().wasCreatedWithShortNotice()).isFalse();
    }

    // ---- al crear ----

    private void stubCreation(GymClosure closure, List<Reservation> cancelled, List<AppUser> members) {
        when(gymClosureRepository.findById(CLOSURE_ID)).thenReturn(Optional.of(closure));
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(gym));
        when(reservationRepository.findByCancelledByClosureId(CLOSURE_ID)).thenReturn(cancelled);
        when(appUserRepository.findAllById(any())).thenReturn(List.of());
        // opcional: los cierres que no avisan a todos nunca llegan a pedir la lista de socios
        org.mockito.Mockito.lenient().when(appUserRepository.findByGymIdAndRole(GYM_ID, Role.MEMBER)).thenReturn(members);
    }

    @Test
    void aClosureForTomorrowAnnouncesToEveryActiveMemberExceptThoseAlreadyToldTheirOwnDetail() {
        stubCreation(closure(1, 1, true),
                List.of(Reservation.builder().gymBlockId(9L).memberId(30L).classDate(TODAY.plusDays(1)).build()),
                List.of(member(30, true), member(31, true), member(32, true), member(33, false)));

        closureNotificationService.notify(CLOSURE_ID, Role.GYM_ADMIN);

        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushService).notifyUsers(eq(List.of(31L, 32L)), message.capture());
        assertThat(message.getValue().title()).isEqualTo("XaloGym cerrará mañana");
        assertThat(message.getValue().body()).isEqualTo("Corte de luz");
    }

    @Test
    void aClosureForTodayIsAnnouncedAsAlreadyClosedAndAsUrgent() {
        stubCreation(closure(0, 0, true), List.of(), List.of(member(31, true)));

        closureNotificationService.notify(CLOSURE_ID, Role.GYM_ADMIN);

        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushService).notifyUsers(eq(List.of(31L)), message.capture());
        assertThat(message.getValue().title()).isEqualTo("XaloGym está cerrado hoy");
        assertThat(message.getValue().urgency()).isEqualTo(PushMessage.Urgency.HIGH);
    }

    @Test
    void aMultiDayClosureSaysUntilWhen() {
        stubCreation(closure(1, 3, true), List.of(), List.of(member(31, true)));

        closureNotificationService.notify(CLOSURE_ID, Role.GYM_ADMIN);

        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushService).notifyUsers(anyCollection(), message.capture());
        assertThat(message.getValue().title()).startsWith("XaloGym cerrará desde mañana hasta el ");
    }

    @Test
    void aClosureScheduledTwoOrMoreDaysAheadAnnouncesToNobodyElse() {
        stubCreation(closure(2, 2, true), List.of(), List.of(member(31, true)));

        closureNotificationService.notify(CLOSURE_ID, Role.GYM_ADMIN);

        verify(pushService, never()).notifyUsers(anyCollection(), any());
        verify(appUserRepository, never()).findByGymIdAndRole(GYM_ID, Role.MEMBER);
    }

    @Test
    void aShortNoticeClosureOfSpecificClassesAnnouncesToNobodyElse() {
        stubCreation(closure(1, 1, false), List.of(), List.of(member(31, true)));

        closureNotificationService.notify(CLOSURE_ID, Role.GYM_ADMIN);

        verify(pushService, never()).notifyUsers(anyCollection(), any());
    }

    @Test
    void aPausedGymAnnouncesNothing() {
        Gym paused = Gym.builder().id(GYM_ID).name("XaloGym").active(false).build();
        when(gymClosureRepository.findById(CLOSURE_ID)).thenReturn(Optional.of(closure(1, 1, true)));
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(paused));
        when(reservationRepository.findByCancelledByClosureId(CLOSURE_ID)).thenReturn(List.of());
        when(appUserRepository.findAllById(any())).thenReturn(List.of());

        closureNotificationService.notify(CLOSURE_ID, Role.GYM_ADMIN);

        verify(pushService, never()).notifyUsers(anyCollection(), any());
    }

    @Test
    void aFailingBroadcastNeverBreaksTheClosure() {
        stubCreation(closure(1, 1, true), List.of(), List.of(member(31, true)));
        org.mockito.Mockito.doThrow(new RuntimeException("push caido")).when(pushService).notifyUsers(anyCollection(), any());

        closureNotificationService.notify(CLOSURE_ID, Role.GYM_ADMIN); // no lanza
    }

    // ---- al levantar ----

    @Test
    void liftingAShortNoticeClosureTellsEveryActiveMember() {
        GymClosure closure = closure(1, 1, true);
        when(appUserRepository.findByGymIdAndRole(GYM_ID, Role.MEMBER))
                .thenReturn(List.of(member(30, true), member(31, true), member(33, false)));

        memberChangeNotifier.closureLifted(gym, closure);

        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushService).notifyUsers(eq(List.of(30L, 31L)), message.capture());
        assertThat(message.getValue().title()).isEqualTo("El cierre de XaloGym terminó");
        assertThat(message.getValue().body()).isEqualTo("El cierre por corte de luz ya terminó. Ahora operamos con normalidad.");
        verify(reservationRepository, never()).findByCancelledByClosureId(any());
    }

    @Test
    void liftingAClosureScheduledAheadTellsOnlyTheAffectedMembers() {
        GymClosure closure = closure(5, 5, true);
        when(reservationRepository.findByCancelledByClosureId(CLOSURE_ID))
                .thenReturn(List.of(Reservation.builder().memberId(30L).build(), Reservation.builder().memberId(30L).build()));

        memberChangeNotifier.closureLifted(gym, closure);

        verify(pushService).notifyUsers(eq(List.of(30L)), any());
        verify(appUserRepository, never()).findByGymIdAndRole(any(), any());
    }
}
