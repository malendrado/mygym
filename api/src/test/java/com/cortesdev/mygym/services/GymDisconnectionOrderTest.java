package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymDeletionAudit;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.GymDisconnectRequest;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymDeletionAuditRepository;
import com.cortesdev.mygym.repositories.GymPhotoRepository;
import com.cortesdev.mygym.repositories.GymPlanRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ManualPaymentRepository;
import com.cortesdev.mygym.repositories.PaymentRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.services.exception.GymDisconnectionEmailFailedException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Desvincular un gimnasio: el borrado se ejecuta (y se fuerza a la base) ANTES de mandar los correos de "tus datos
 * fueron eliminados" — así un borrado rechazado por la base no deja avisos falsos (auditoría 2026-10-17).
 */
@ExtendWith(MockitoExtension.class)
class GymDisconnectionOrderTest {

    private static final Long GYM_ID = 5L;

    @Mock private GymRepository gymRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private GymBlockRepository gymBlockRepository;
    @Mock private GymPlanRepository gymPlanRepository;
    @Mock private GymPhotoRepository gymPhotoRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ManualPaymentRepository manualPaymentRepository;
    @Mock private GymDeletionAuditRepository gymDeletionAuditRepository;
    @Mock private GymDisconnectionEmailService emailService;
    @Mock private MemberService memberService;
    @org.mockito.Spy private com.cortesdev.mygym.services.MygymSettingsService mygymSettings = com.cortesdev.mygym.services.TestMygymSettings.defaults();
    @InjectMocks private GymDisconnectionService service;

    private final Gym gym = Gym.builder().id(GYM_ID).name("XaloGym").slug("xalogym").build();

    @BeforeEach
    void gymWithOneAdmin() {
        when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(gym));
        when(appUserRepository.findByGymId(GYM_ID))
                .thenReturn(List.of(AppUser.builder().id(1L).email("dueno@x.cl").role(Role.GYM_ADMIN).gymId(GYM_ID).build()));
        when(gymDeletionAuditRepository.save(any(GymDeletionAudit.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void theDeletionIsForcedToTheDatabaseBeforeAnyEmailGoesOut() {
        when(emailService.sendToGymAdmin(anyString(), any(), any(), anyString(), anyString())).thenReturn(true);

        service.disconnect(GYM_ID, new GymDisconnectRequest("XaloGym"), "super@mygym.cl");

        var order = inOrder(gymRepository, emailService);
        order.verify(gymRepository).delete(gym);
        order.verify(gymRepository).flush();
        order.verify(emailService).sendToGymAdmin(anyString(), any(), any(), anyString(), anyString());
    }

    @Test
    void ifTheDatabaseRejectsTheDeletionNoEmailIsSent() {
        doThrow(new IllegalStateException("violación de clave foránea")).when(gymRepository).flush();

        assertThatThrownBy(() -> service.disconnect(GYM_ID, new GymDisconnectRequest("XaloGym"), "super@mygym.cl"))
                .isInstanceOf(IllegalStateException.class);

        verify(emailService, never()).sendToGymAdmin(anyString(), any(), any(), anyString(), anyString());
        verify(emailService, never()).sendSummaryToSuperAdmin(anyString(), any(), any());
    }

    @Test
    void ifAnAdminEmailFailsTheWholeThingAbortsSoTheTransactionRollsTheDeletionBack() {
        when(emailService.sendToGymAdmin(anyString(), any(), any(), anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.disconnect(GYM_ID, new GymDisconnectRequest("XaloGym"), "super@mygym.cl"))
                .isInstanceOf(GymDisconnectionEmailFailedException.class);

        verify(emailService, never()).sendSummaryToSuperAdmin(anyString(), any(), any());
    }
}
