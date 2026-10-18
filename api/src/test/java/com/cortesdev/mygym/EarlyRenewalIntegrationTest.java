package com.cortesdev.mygym;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.GymPlan;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.MemberResponse;
import com.cortesdev.mygym.models.dto.ReservationCreateRequest;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymPlanRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.services.MemberService;
import com.cortesdev.mygym.services.MembershipPeriods;
import com.cortesdev.mygym.services.ReservationService;
import com.cortesdev.mygym.services.exception.BookingWindowClosedException;
import com.cortesdev.mygym.services.exception.MonthlyQuotaExceededException;
import com.cortesdev.mygym.services.exception.SubscriptionRequiredException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Renovación anticipada contra la base real (V52): las columnas nuevas se guardan y leen, y reservar cuenta cada clase
 * contra el cupo del período que contiene su fecha. No transaccional a propósito (como en producción); al final se borra
 * el gimnasio y sus filas hijas caen en cascada.
 */
@SpringBootTest
class EarlyRenewalIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("America/Santiago");

    @Autowired private ReservationService reservationService;
    @Autowired private MemberService memberService;
    @Autowired private GymRepository gymRepository;
    @Autowired private GymBlockRepository gymBlockRepository;
    @Autowired private GymPlanRepository gymPlanRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private ReservationRepository reservationRepository;

    private Long gymId;

    @AfterEach
    void cleanUp() {
        if (gymId != null) {
            gymRepository.deleteById(gymId);
        }
    }

    private void newGym() {
        gymId = gymRepository
                .save(Gym.builder()
                        .name("Gym renovación").slug("gym-renovacion-" + System.nanoTime()).maxUsers(100).active(true)
                        .googleLoginEnabled(true).bookingWindowMinutes(0).cancellationWindowMinutes(0).build())
                .getId();
    }

    private GymPlan plan(Integer monthlyClasses) {
        return gymPlanRepository.save(GymPlan.builder()
                .gymId(gymId).name("Plan").priceClp(20_000).monthlyClasses(monthlyClasses).active(true).build());
    }

    private AppUser member(GymPlan plan, ZonedDateTime paidAt) {
        return appUserRepository.save(AppUser.builder()
                .email("socio-" + System.nanoTime() + "@test.com").name("Socio").role(Role.MEMBER).gymId(gymId)
                .active(true).planId(plan == null ? null : plan.getId()).paidAt(paidAt == null ? null : paidAt.toInstant())
                .rut("12.345.678-5").address("Av. Siempre Viva 742, Ñuñoa").phone("+56912345678")
                .birthDate(LocalDate.of(1990, 5, 20)).emergencyContactName("Marta Pérez")
                .emergencyContactPhone("+56987654321").build());
    }

    private void book(AppUser member, LocalDate date, int hour) {
        GymBlock block = gymBlockRepository.save(GymBlock.builder()
                .gymId(gymId).label("Clase " + date + " " + hour).dayOfWeek(date.getDayOfWeek())
                .startTime(LocalTime.of(hour, 0)).endTime(LocalTime.of(hour + 1, 0)).capacity(10).active(true).build());
        reservationService.book(gymId, member.getId(), new ReservationCreateRequest(block.getId(), date));
    }

    /** Pagó hace 20 días y renueva por adelantado ahora (misma operación que Flow y "Renovar" del admin). */
    private AppUser renewedEarly(GymPlan plan) {
        AppUser m = member(plan, ZonedDateTime.now(ZONE).minusDays(20));
        MembershipPeriods.applyPayment(m, plan == null ? null : plan.getId(), Instant.now(), true);
        return appUserRepository.save(m);
    }

    @Test
    void lasColumnasNuevasSeGuardanYLaConsultaDelCronLasEncuentra() {
        newGym();
        AppUser m = renewedEarly(plan(8));

        AppUser reloaded = appUserRepository.findById(m.getId()).orElseThrow();

        assertThat(reloaded.getPeriodStart()).isCloseTo(m.getPeriodStart(), org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MILLIS));
        assertThat(reloaded.getPaidPeriods()).isEqualTo(2);
        assertThat(reloaded.getPaidAt()).isAfter(reloaded.getPeriodStart());
        assertThat(appUserRepository.findByRoleAndPeriodStartIsNotNull(Role.MEMBER))
                .extracting(AppUser::getId)
                .contains(m.getId());
        // un socio común (columnas en NULL) se comporta como siempre
        AppUser plain = member(plan(8), ZonedDateTime.now(ZONE).minusDays(5));
        AppUser plainReloaded = appUserRepository.findById(plain.getId()).orElseThrow();
        assertThat(plainReloaded.getPeriodStart()).isNull();
        assertThat(plainReloaded.getPaidPeriods()).isNull();
        assertThat(MembershipPeriods.paidPeriods(plainReloaded)).isEqualTo(1);
    }

    @Test
    void reservarCuentaContraElCupoDelPeriodoQueContieneLaFechaDeLaClase() {
        newGym();
        GymPlan plan = plan(2);
        AppUser m = renewedEarly(plan);
        LocalDate curEnd = MembershipPeriods.currentEnd(m, ZonedDateTime.now(ZONE)).toLocalDate();
        LocalDate finalLastDay = MembershipPeriods.finalEnd(m).toLocalDate().minusDays(1);

        // dos clases del período ACTUAL agotan su cupo
        book(m, curEnd.minusDays(1), 10);
        book(m, curEnd.minusDays(2), 10);
        assertThatThrownBy(() -> book(m, curEnd.minusDays(3), 10)).isInstanceOf(MonthlyQuotaExceededException.class);

        // el cupo NUEVO está completo y parte en cero: la clase del primer día nuevo y otra más entran
        book(m, curEnd, 10);
        book(m, curEnd.plusDays(1), 10);
        assertThatThrownBy(() -> book(m, curEnd.plusDays(2), 10)).isInstanceOf(MonthlyQuotaExceededException.class);

        // y no se puede reservar más allá del vencimiento final
        assertThatThrownBy(() -> book(m, finalLastDay.plusDays(1), 10)).isInstanceOf(BookingWindowClosedException.class);

        AppUser reloaded = appUserRepository.findById(m.getId()).orElseThrow();
        assertThat(memberService.remainingClasses(reloaded)).isZero();
        assertThat(memberService.remainingClassesInQueuedPeriod(reloaded)).isZero();
        assertThat(reservationRepository.countByMemberIdAndStatusAndClassDateBetween(
                        m.getId(), ReservationStatus.BOOKED, curEnd, finalLastDay))
                .isEqualTo(2);
    }

    @Test
    void sinRenovarPorAdelantadoUnaClaseDespuesDelVencimientoSigueRechazada() {
        newGym();
        AppUser m = member(plan(8), ZonedDateTime.now(ZONE).minusDays(20));
        LocalDate curEnd = MembershipPeriods.currentEnd(m, ZonedDateTime.now(ZONE)).toLocalDate();

        assertThatThrownBy(() -> book(m, curEnd, 10)).isInstanceOf(BookingWindowClosedException.class);
        book(m, curEnd.minusDays(1), 10);
    }

    @Test
    void unPlanLibreReservaEnElPeriodoNuevoPeroNoMasAllaDelVencimientoFinal() {
        newGym();
        AppUser m = renewedEarly(plan(null));
        LocalDate curEnd = MembershipPeriods.currentEnd(m, ZonedDateTime.now(ZONE)).toLocalDate();
        LocalDate finalLastDay = MembershipPeriods.finalEnd(m).toLocalDate().minusDays(1);

        book(m, curEnd.minusDays(1), 10);
        book(m, curEnd, 10);
        book(m, finalLastDay, 10);

        assertThatThrownBy(() -> book(m, finalLastDay.plusDays(1), 10)).isInstanceOf(BookingWindowClosedException.class);
    }

    @Test
    void conPlanVencidoNoSePuedeReservarYElEstadoEsVencido() {
        newGym();
        AppUser m = member(plan(8), ZonedDateTime.now(ZONE).minusDays(90));

        assertThatThrownBy(() -> book(m, LocalDate.now(ZONE).plusDays(2), 10))
                .isInstanceOf(SubscriptionRequiredException.class);
        MemberResponse r = memberService.getOwnMembership(m.getId());
        assertThat(r.membershipStatus()).isEqualTo("EXPIRED");
        assertThat(r.canRenewEarly()).isFalse();
    }

    @Test
    void nuncaPagadoNoPuedeReservarYNoPuedeRenovarPorAdelantado() {
        newGym();
        AppUser m = member(null, null);

        assertThatThrownBy(() -> book(m, LocalDate.now(ZONE).plusDays(2), 10))
                .isInstanceOf(SubscriptionRequiredException.class);
        MemberResponse r = memberService.getOwnMembership(m.getId());
        assertThat(r.membershipStatus()).isEqualTo("UNPAID");
        assertThat(r.canRenewEarly()).isFalse();
        assertThat(r.earlyRenewalStartsOn()).isNull();
    }

    @Test
    void elDtoDelSocioReflejaLaColaContraLaBaseReal() {
        newGym();
        AppUser m = renewedEarly(plan(8));
        ZonedDateTime anchor = MembershipPeriods.anchor(m);

        MemberResponse r = memberService.getOwnMembership(m.getId());

        assertThat(r.planEndDate()).isCloseTo(anchor.plusMonths(2).toInstant(), org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MILLIS));
        assertThat(r.queuedPeriodEnd()).isCloseTo(anchor.plusMonths(2).toInstant(), org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MILLIS));
        assertThat(r.currentPeriodEnd()).isCloseTo(anchor.plusMonths(1).toInstant(), org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MILLIS));
        assertThat(r.canRenewEarly()).isFalse();
        assertThat(r.sessionsRemaining()).isEqualTo(8);
        assertThat(r.queuedSessionsRemaining()).isEqualTo(8);
        assertThat(List.of("ACTIVE", "EXPIRING_SOON")).contains(r.membershipStatus());
    }
}
