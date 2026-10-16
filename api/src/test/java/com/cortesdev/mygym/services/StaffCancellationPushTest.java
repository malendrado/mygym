package com.cortesdev.mygym.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.ClassWaitlistEntry;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.ClassWaitlistRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.services.PushNotificationService.Delivery;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Aviso push al admin y profesores cuando un SOCIO cancela (pedido del usuario 2026-10-13). */
@ExtendWith(MockitoExtension.class)
class StaffCancellationPushTest {

    private static final ZoneId ZONE = ZoneId.of("America/Santiago");
    private static final Long GYM_ID = 5L;
    private static final Long BLOCK_ID = 9L;

    @Mock private PushNotificationService pushService;
    @Mock private AppUserRepository appUserRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private ClassWaitlistRepository waitlistRepository;
    @InjectMocks private StaffCancellationPush staffPush;

    private final AppUser member = AppUser.builder().id(30L).name("Ana Soto").role(Role.MEMBER).gymId(GYM_ID).active(true).build();

    private GymBlock blockStartingIn(long minutes, LocalDate[] dateOut) {
        ZonedDateTime start = ZonedDateTime.now(ZONE).plusMinutes(minutes);
        dateOut[0] = start.toLocalDate();
        return GymBlock.builder()
                .id(BLOCK_ID)
                .gymId(GYM_ID)
                .label("Funcional")
                .capacity(10)
                .startTime(LocalTime.of(start.getHour(), start.getMinute()))
                .build();
    }

    private AppUser staff(long id, Role role, boolean active) {
        return AppUser.builder().id(id).role(role).gymId(GYM_ID).active(active).name("Staff " + id).build();
    }

    @SuppressWarnings("unchecked")
    private Delivery capturedDelivery() {
        ArgumentCaptor<Supplier<Delivery>> captor = ArgumentCaptor.forClass(Supplier.class);
        verify(pushService).notifyLater(captor.capture());
        return captor.getValue().get();
    }

    @Test
    void aClassThatAlreadyStartedSendsNothing() {
        LocalDate[] date = new LocalDate[1];
        GymBlock block = blockStartingIn(-30, date);

        staffPush.memberCancelled(block, date[0], member);

        verify(pushService, never()).notifyLater(any());
    }

    @Test
    void itGoesToEveryActiveAdminAndTeacherOfTheGymWithTheRemainingSpots() {
        LocalDate[] date = new LocalDate[1];
        GymBlock block = blockStartingIn(180, date);
        when(appUserRepository.findByGymIdAndRole(GYM_ID, Role.GYM_ADMIN)).thenReturn(List.of(staff(1, Role.GYM_ADMIN, true)));
        when(appUserRepository.findByGymIdAndRole(GYM_ID, Role.PROFESOR))
                .thenReturn(List.of(staff(2, Role.PROFESOR, true), staff(3, Role.PROFESOR, false)));
        when(reservationRepository.countByGymBlockIdAndClassDateAndStatus(BLOCK_ID, date[0], ReservationStatus.BOOKED)).thenReturn(7);
        when(waitlistRepository.findByGymBlockIdAndClassDateOrderByCreatedAtAsc(BLOCK_ID, date[0]))
                .thenReturn(List.of(ClassWaitlistEntry.builder().memberId(99L).build()));
        when(reservationRepository.countByGymBlockIdAndClassDateAndStatusAndCancelledByClosureIdIsNullAndUpdatedAtAfter(
                        eq(BLOCK_ID), eq(date[0]), eq(ReservationStatus.CANCELLED), any(Instant.class)))
                .thenReturn(1);

        staffPush.memberCancelled(block, date[0], member);
        Delivery delivery = capturedDelivery();

        assertEquals(List.of(1L, 2L), List.copyOf(delivery.userIds())); // el profesor inactivo no recibe nada
        PushMessage message = delivery.message();
        assertTrue(message.title().startsWith("Cancelación · Funcional "), message.title());
        assertTrue(message.body().startsWith("Ana Soto canceló ("), message.body());
        assertTrue(message.body().endsWith("Quedan 3 cupos · 1 en espera."), message.body());
        assertEquals("cancel-9-" + date[0], message.tag());
        assertEquals("/gym-admin", message.url());
    }

    @Test
    void severalRecentCancellationsOfTheSameClassAreCountedInOneNotification() {
        LocalDate[] date = new LocalDate[1];
        GymBlock block = blockStartingIn(180, date);
        when(appUserRepository.findByGymIdAndRole(GYM_ID, Role.GYM_ADMIN)).thenReturn(List.of(staff(1, Role.GYM_ADMIN, true)));
        when(appUserRepository.findByGymIdAndRole(GYM_ID, Role.PROFESOR)).thenReturn(List.of());
        when(reservationRepository.countByGymBlockIdAndClassDateAndStatus(BLOCK_ID, date[0], ReservationStatus.BOOKED)).thenReturn(10);
        when(waitlistRepository.findByGymBlockIdAndClassDateOrderByCreatedAtAsc(BLOCK_ID, date[0])).thenReturn(List.of());
        when(reservationRepository.countByGymBlockIdAndClassDateAndStatusAndCancelledByClosureIdIsNullAndUpdatedAtAfter(
                        eq(BLOCK_ID), eq(date[0]), eq(ReservationStatus.CANCELLED), any(Instant.class)))
                .thenReturn(3);

        staffPush.memberCancelled(block, date[0], member);
        PushMessage message = capturedDelivery().message();

        assertTrue(message.title().startsWith("3 cancelaciones · Funcional"), message.title());
        assertTrue(message.body().startsWith("Ana Soto y 2 más cancelaron"), message.body());
        assertTrue(message.body().contains("La clase ya se volvió a llenar"), message.body());
    }

    @Test
    void aGymWithoutAdminsOrTeachersSendsNothing() {
        LocalDate[] date = new LocalDate[1];
        GymBlock block = blockStartingIn(180, date);
        when(appUserRepository.findByGymIdAndRole(GYM_ID, Role.GYM_ADMIN)).thenReturn(List.of());
        when(appUserRepository.findByGymIdAndRole(GYM_ID, Role.PROFESOR)).thenReturn(List.of());

        staffPush.memberCancelled(block, date[0], member);

        assertNull(capturedDelivery());
    }
}
