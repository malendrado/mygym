package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymPlan;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymPlanRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Avisos de vencimiento con renovación anticipada: usan el vencimiento FINAL, así que quien ya pagó el período siguiente no
 * recibe "vence en N días" (Mockito, sin DB). MemberService es el real: lo que se prueba es la cuenta de períodos.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MembershipReminderEarlyRenewalTest {

    private static final ZoneId ZONE = ZoneId.of("America/Santiago");
    private static final Long GYM_ID = 2L;
    private static final Long PLAN_ID = 5L;

    @Mock private AppUserRepository appUserRepository;
    @Mock private GymRepository gymRepository;
    @Mock private GymPlanRepository gymPlanRepository;
    @Mock private MemberLifecycleEmailService emailService;
    @Mock private PushNotificationService pushService;
    @InjectMocks private MemberService memberService;

    private MembershipReminderJob job;
    private final Gym gym = Gym.builder().id(GYM_ID).name("XaloGym").active(true).build();
    private final GymPlan plan = GymPlan.builder().id(PLAN_ID).gymId(GYM_ID).name("Plan Básico").monthlyClasses(8).build();

    @BeforeEach
    void setUp() {
        job = new MembershipReminderJob(
                appUserRepository, memberService, gymRepository, gymPlanRepository, emailService, pushService);
        when(gymPlanRepository.findAllById(any())).thenReturn(List.of(plan));
        when(gymRepository.findAllById(any())).thenReturn(List.of(gym));
        when(appUserRepository.findByGymIdAndRole(GYM_ID, Role.GYM_ADMIN)).thenReturn(List.of());
    }

    /** Socio con una cadena de n meses cuyo vencimiento final cae (por fecha de Chile) dentro de `daysAhead` días. */
    private AppUser chain(long id, int n, int daysAhead) {
        LocalDate target = LocalDate.now(ZONE).plusDays(daysAhead);
        ZonedDateTime anchor = ZonedDateTime.now(ZONE).plusDays(daysAhead).minusMonths(n);
        // El fin de mes puede achicar el último mes: se corre el ancla unos días hasta que el fin caiga en la fecha pedida.
        for (int shift = 0; shift < 4 && !MembershipPeriods.endOf(anchor, n).toLocalDate().equals(target); shift++) {
            anchor = anchor.plusDays(1);
        }
        assertThat(MembershipPeriods.endOf(anchor, n).toLocalDate()).isEqualTo(target);
        AppUser u = AppUser.builder().id(id).gymId(GYM_ID).role(Role.MEMBER).planId(PLAN_ID).email("s" + id + "@x.cl")
                .paidAt(anchor.toInstant()).build();
        if (n > 1) {
            u.setPeriodStart(anchor.toInstant());
            u.setPaidPeriods(n);
            u.setPaidAt(anchor.plusDays(3).toInstant()); // el pago real no tiene nada que ver con el vencimiento final
        }
        return u;
    }

    private void worldHas(AppUser... legacyWindow) {
        when(appUserRepository.findByRoleAndPaidAtBetween(eq(Role.MEMBER), any(), any())).thenReturn(List.of(legacyWindow));
    }

    private void chainedHas(AppUser... chained) {
        when(appUserRepository.findByRoleAndPeriodStartIsNotNull(Role.MEMBER)).thenReturn(List.of(chained));
    }

    @Test
    void quienYaRenovoPorAdelantadoNoRecibeAvisoDeVencimientoDelPeriodoActual() {
        // El período actual vence en 2 días, pero pagó el siguiente: el vencimiento final está a un mes de distancia.
        AppUser renewed = chain(1, 2, 32);
        ZonedDateTime anchor = MembershipPeriods.anchor(renewed);
        // forzamos que el período actual (el 1) venza en ~2 días: ancla = hoy + 2 días - 1 mes
        ZonedDateTime fixedAnchor = ZonedDateTime.now(ZONE).plusDays(2).minusMonths(1);
        renewed.setPeriodStart(fixedAnchor.toInstant());
        renewed.setPaidPeriods(2);
        chainedHas(renewed);
        worldHas();

        assertThat(MembershipPeriods.currentEnd(renewed, ZonedDateTime.now(ZONE)).toLocalDate())
                .isEqualTo(LocalDate.now(ZONE).plusDays(2));
        assertThat(anchor).isNotNull();

        job.sendReminders();

        verify(emailService, never()).sendMembershipExpiringSoonMember(any(), any(), any(), anyLong());
        verify(emailService, never()).sendMembershipExpiredMember(any(), any(), any(), anyBoolean());
        verify(pushService, never()).notifyUsers(any(), any());
    }

    @Test
    void quienEstaEnSuUltimoPeriodoEncoladoRecibeElAvisoPorElVencimientoFinal() {
        AppUser lastPeriod = chain(2, 2, 2); // ya pasó al período 2 (el que había pagado por adelantado) y vence en 2 días
        chainedHas(lastPeriod);
        worldHas();

        job.sendReminders();

        verify(emailService).sendMembershipExpiringSoonMember(gym, lastPeriod, plan, 2L);
    }

    @Test
    void elVencimientoFinalDeHoyDisparaElAvisoDeVencido() {
        AppUser today = chain(3, 3, 0);
        chainedHas(today);
        worldHas();

        job.sendReminders();

        verify(emailService).sendMembershipExpiredMember(eq(gym), eq(today), eq(plan), anyBoolean());
        verify(emailService, never()).sendMembershipExpiringSoonMember(any(), any(), any(), anyLong());
    }

    @Test
    void unaCadenaVencidaHaceTiempoNoAvisaNada() {
        AppUser longGone = chain(4, 2, -40);
        chainedHas(longGone);
        worldHas();

        job.sendReminders();

        verify(emailService, never()).sendMembershipExpiringSoonMember(any(), any(), any(), anyLong());
        verify(emailService, never()).sendMembershipExpiredMember(any(), any(), any(), anyBoolean());
    }

    @Test
    void unSocioSinRenovacionAnticipadaSigueAvisandoPorSuPago() {
        AppUser legacy = chain(5, 1, 3); // N=1, sin period_start: comportamiento de siempre
        worldHas(legacy);
        chainedHas();

        job.sendReminders();

        verify(emailService).sendMembershipExpiringSoonMember(gym, legacy, plan, 3L);
    }

    @Test
    void unSocioQueApareceEnAmbasConsultasSeAvisaUnaSolaVez() {
        AppUser both = chain(6, 2, 1);
        worldHas(both);
        chainedHas(both);

        job.sendReminders();

        verify(emailService, times(1)).sendMembershipExpiringSoonMember(gym, both, plan, 1L);
        verify(pushService, times(1)).notifyUsers(anyList(), any());
    }
}
