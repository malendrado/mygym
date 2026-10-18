package com.cortesdev.mygym;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.models.GymPlan;
import com.cortesdev.mygym.models.ManualPayment;
import com.cortesdev.mygym.models.Payment;
import com.cortesdev.mygym.models.Reservation;
import com.cortesdev.mygym.models.ReservationStatus;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.MemberResponse;
import com.cortesdev.mygym.models.dto.ReservationCreateRequest;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymBlockRepository;
import com.cortesdev.mygym.repositories.GymPlanRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.ManualPaymentRepository;
import com.cortesdev.mygym.repositories.PaymentRepository;
import com.cortesdev.mygym.repositories.ReservationRepository;
import com.cortesdev.mygym.services.FlowApiClient;
import com.cortesdev.mygym.services.FlowPaymentService;
import com.cortesdev.mygym.services.GymService;
import com.cortesdev.mygym.services.MemberLifecycleEmailService;
import com.cortesdev.mygym.services.MemberService;
import com.cortesdev.mygym.services.MembershipPeriods;
import com.cortesdev.mygym.services.PushNotificationService;
import com.cortesdev.mygym.services.ReservationService;
import com.cortesdev.mygym.services.exception.PaymentConflictException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Cadena de períodos pagados contra la base REAL: pagos simultáneos del mismo socio (webhooks de Flow y "Renovar" del
 * admin) no pierden meses porque la fila del socio se bloquea; idempotencia del pago manual con la consulta real; aviso
 * de clases agotadas por período; saldo del día del borde y revokePlan con cola. No transaccional a propósito (como en
 * producción); al final se borra el gimnasio y sus filas hijas caen en cascada.
 */
@SpringBootTest
class PaymentChainIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("America/Santiago");

    @MockitoBean private FlowApiClient flowApiClient;
    @MockitoBean private MemberLifecycleEmailService emailService;
    @MockitoBean private PushNotificationService pushService;

    @Autowired private GymService gymService;
    @Autowired private FlowPaymentService flowPaymentService;
    @Autowired private ReservationService reservationService;
    @Autowired private MemberService memberService;
    @Autowired private GymRepository gymRepository;
    @Autowired private GymBlockRepository gymBlockRepository;
    @Autowired private GymPlanRepository gymPlanRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private ManualPaymentRepository manualPaymentRepository;

    private Long gymId;

    @AfterEach
    void cleanUp() {
        Mockito.reset(emailService, pushService, flowApiClient);
        if (gymId != null) {
            gymRepository.deleteById(gymId);
        }
    }

    private void newGym() {
        gymId = gymRepository
                .save(Gym.builder()
                        .name("Gym cadena").slug("gym-cadena-" + System.nanoTime()).maxUsers(100).active(true)
                        .googleLoginEnabled(true).bookingWindowMinutes(0).cancellationWindowMinutes(0)
                        .flowApiKey("k").flowSecretKey("s").build())
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

    private AppUser reload(AppUser m) {
        return appUserRepository.findById(m.getId()).orElseThrow();
    }

    private void book(AppUser member, LocalDate date) {
        GymBlock block = gymBlockRepository.save(GymBlock.builder()
                .gymId(gymId).label("Clase " + date).dayOfWeek(date.getDayOfWeek())
                .startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(11, 0)).capacity(10).active(true).build());
        reservationService.book(gymId, member.getId(), new ReservationCreateRequest(block.getId(), date));
    }

    /** Lanza todas las tareas a la vez (latch) y devuelve cuántas terminaron sin excepción. */
    private int runAtTheSameTime(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (Callable<Object> task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    task.call();
                    succeeded.incrementAndGet();
                } catch (Exception failure) {
                    failure.printStackTrace();
                }
                return null;
            }));
        }
        ready.await(10, TimeUnit.SECONDS);
        go.countDown();
        for (Future<?> future : futures) {
            future.get(90, TimeUnit.SECONDS);
        }
        pool.shutdown();
        return succeeded.get();
    }

    // ---- 1. pagos simultáneos del mismo socio

    @Test
    void tresRenovacionesManualesSimultaneasDelMismoSocioAcreditanTresMeses() throws Exception {
        newGym();
        GymPlan plan = plan(8);
        AppUser m = member(plan, ZonedDateTime.now(ZONE).minusDays(5)); // plan vigente, 1 mes pagado
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String bank = "Banco " + i; // métodos distintos: no es un doble clic, son tres pagos reales
            tasks.add(() -> {
                gymService.simulatePlanPayment(gymId, m.getId(), plan.getId(), bank, null);
                return null;
            });
        }

        int succeeded = runAtTheSameTime(tasks);

        AppUser after = reload(m);
        assertThat(succeeded).isEqualTo(3);
        assertThat(after.getPaidPeriods()).isEqualTo(4); // 1 que ya tenía + 3; sin el bloqueo se pisaban y se perdían meses
        assertThat(MembershipPeriods.hasQueued(after, ZonedDateTime.now(ZONE))).isTrue();
        assertThat(manualPaymentRepository.findByMemberIdIn(List.of(m.getId()))).hasSize(3);
    }

    @Test
    void doblePagoSimultaneoFlowYManualDelMismoSocioAcreditaDosMesesSinDeadlock() throws Exception {
        newGym();
        GymPlan plan = plan(8);
        AppUser m = member(plan, ZonedDateTime.now(ZONE).minusDays(5));
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            String token = "tok-" + System.nanoTime() + "-" + i;
            tokens.add(token);
            paymentRepository.save(Payment.builder()
                    .memberId(m.getId()).planId(plan.getId()).commerceOrder("order-" + token).flowToken(token)
                    .amountClp(20_000).status("PENDING").build());
        }
        when(flowApiClient.get(eq("/payment/getStatus"), anyMap(), any(), any())).thenAnswer(inv -> {
            Map<String, String> params = inv.getArgument(1);
            return Map.of("status", 2, "commerceOrder", "order-" + params.get("token"), "amount", "20000");
        });
        List<Callable<Object>> tasks = new ArrayList<>();
        for (String token : tokens) {
            tasks.add(() -> {
                flowPaymentService.handleWebhook(token);
                return null;
            });
        }
        for (int i = 0; i < 2; i++) {
            String bank = "Banco " + i;
            tasks.add(() -> {
                gymService.simulatePlanPayment(gymId, m.getId(), plan.getId(), bank, null);
                return null;
            });
        }

        int succeeded = runAtTheSameTime(tasks);

        AppUser after = reload(m);
        assertThat(succeeded).isEqualTo(4);
        assertThat(after.getPaidPeriods()).isEqualTo(5);
        assertThat(paymentRepository.findAll().stream().filter(p -> tokens.contains(p.getFlowToken())))
                .allSatisfy(p -> assertThat(p.getStatus()).isEqualTo("PAID"));
    }

    // ---- 5. idempotencia del pago manual con la consulta real

    @Test
    void unSegundoPagoIgualDentroDe15SegundosEs409YFueraDeLaVentanaAcredita() {
        newGym();
        GymPlan plan = plan(8);
        AppUser m = member(plan, ZonedDateTime.now(ZONE).minusDays(5));

        gymService.simulatePlanPayment(gymId, m.getId(), plan.getId(), "Banco Estado", null);
        assertThatThrownBy(() -> gymService.simulatePlanPayment(gymId, m.getId(), plan.getId(), "Banco Estado", null))
                .isInstanceOfSatisfying(PaymentConflictException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(PaymentConflictException.CODE_DUPLICATE_PAYMENT);
                    assertThat(e.getMessage()).isEqualTo("Ya registraste este pago hace un momento.");
                });
        assertThat(reload(m).getPaidPeriods()).isEqualTo(2); // un solo mes más

        // otro método (o plan) no es el mismo pago
        gymService.simulatePlanPayment(gymId, m.getId(), plan.getId(), "Efectivo", null);
        assertThat(reload(m).getPaidPeriods()).isEqualTo(3);

        // pasada la ventana, el mismo pago vuelve a acreditar
        for (ManualPayment p : manualPaymentRepository.findByMemberIdIn(List.of(m.getId()))) {
            p.setCreatedAt(Instant.now().minusSeconds(20));
            manualPaymentRepository.save(p);
        }
        gymService.simulatePlanPayment(gymId, m.getId(), plan.getId(), "Banco Estado", null);
        assertThat(reload(m).getPaidPeriods()).isEqualTo(4);
    }

    // ---- 2. aviso de clases agotadas por período

    @Test
    void elAvisoDeAgotadoSeMideContraElPeriodoDeLaClaseYTraeElFinDeEsePeriodo() {
        newGym();
        GymPlan plan = plan(2);
        AppUser m = member(plan, ZonedDateTime.now(ZONE).minusDays(20));
        MembershipPeriods.applyPayment(m, plan.getId(), Instant.now(), true);
        m = appUserRepository.save(m);
        ZonedDateTime anchor = MembershipPeriods.anchor(m);
        LocalDate curEnd = MembershipPeriods.currentEnd(m, ZonedDateTime.now(ZONE)).toLocalDate();

        book(m, curEnd.minusDays(2));
        verify(emailService, never()).sendClassesExhaustedMember(any(), any(), any(), any());
        book(m, curEnd.minusDays(1)); // agota el período 1
        verify(emailService, times(1)).sendClassesExhaustedMember(any(), any(), any(), any());

        // una clase del período 2 (con cupo completo) NO re-dispara el aviso del período 1
        book(m, curEnd);
        verify(emailService, times(1)).sendClassesExhaustedMember(any(), any(), any(), any());
        verify(pushService, times(1)).notifyUsers(any(), any());

        // al agotar el período 2 sí avisa, con el fin de ESE período
        book(m, curEnd.plusDays(1));
        ArgumentCaptor<String> until = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(2)).sendClassesExhaustedMember(any(), any(), any(), until.capture());
        String expected = java.time.format.DateTimeFormatter
                .ofPattern("d 'de' MMMM", new java.util.Locale("es", "CL"))
                .format(MembershipPeriods.endOf(anchor, 2).toLocalDate());
        assertThat(until.getAllValues().get(1)).isEqualTo(expected);
        assertThat(until.getAllValues().get(0))
                .isEqualTo(java.time.format.DateTimeFormatter
                        .ofPattern("d 'de' MMMM", new java.util.Locale("es", "CL"))
                        .format(MembershipPeriods.endOf(anchor, 1).toLocalDate()));
    }

    // ---- 7. saldo que se muestra el día del borde

    @Test
    void elDiaDelBordeElSaldoMostradoEsElDelPeriodoSiguienteYElRestoDelTiempoElDelActual() {
        Assumptions.assumeTrue(ZonedDateTime.now(ZONE).toLocalTime().isBefore(LocalTime.of(23, 40)));
        newGym();
        GymPlan plan = plan(2);
        // Período 1 vence HOY a las 23:50 (ya queda sin fechas reservables) y hay un mes en cola. Usó las 2 clases del actual.
        ZonedDateTime paid = ZonedDateTime.now(ZONE).minusMonths(1).withHour(23).withMinute(50).withSecond(0).withNano(0);
        AppUser edge = member(plan, paid);
        MembershipPeriods.applyPayment(edge, plan.getId(), Instant.now(), true);
        edge.setUsedSessionsAtImport(2);
        edge = appUserRepository.save(edge);
        assertThat(MembershipPeriods.currentIndex(edge, ZonedDateTime.now(ZONE))).isEqualTo(1); // por instante sigue el 1

        MemberResponse atEdge = memberService.getOwnMembership(edge.getId());

        assertThat(atEdge.queuedSessionsRemaining()).isEqualTo(2);
        assertThat(atEdge.sessionsRemaining()).isEqualTo(2); // no el 0 del período que ya no tiene fechas

        // control: a mitad del período el saldo mostrado sigue siendo el del actual
        AppUser mid = member(plan, ZonedDateTime.now(ZONE).minusDays(10));
        MembershipPeriods.applyPayment(mid, plan.getId(), Instant.now(), true);
        mid.setUsedSessionsAtImport(2);
        mid = appUserRepository.save(mid);

        MemberResponse midResponse = memberService.getOwnMembership(mid.getId());

        assertThat(midResponse.sessionsRemaining()).isZero();
        assertThat(midResponse.queuedSessionsRemaining()).isEqualTo(2);
    }

    // ---- 8. revokePlan con cola y reservas en el período encolado

    @Test
    void revocarElPlanCancelaLasReservasFuturasDeAmbosPeriodosYAvisa() {
        newGym();
        GymPlan plan = plan(8);
        AppUser m = member(plan, ZonedDateTime.now(ZONE).minusDays(20));
        MembershipPeriods.applyPayment(m, plan.getId(), Instant.now(), true);
        m = appUserRepository.save(m);
        LocalDate curEnd = MembershipPeriods.currentEnd(m, ZonedDateTime.now(ZONE)).toLocalDate();
        book(m, curEnd.minusDays(1)); // período actual
        book(m, curEnd.plusDays(2)); // período encolado
        assertThat(reservationRepository.findByMemberIdAndStatusAndClassDateGreaterThanEqual(
                        m.getId(), ReservationStatus.BOOKED, LocalDate.now(ZONE)))
                .hasSize(2);

        gymService.revokePlan(gymId, m.getId());

        AppUser after = reload(m);
        assertThat(after.getPlanId()).isNull();
        assertThat(after.getPaidAt()).isNull();
        assertThat(after.getPeriodStart()).isNull();
        assertThat(after.getPaidPeriods()).isNull();
        List<Reservation> all = reservationRepository.findByMemberIdAndClassDateBetween(
                m.getId(), LocalDate.now(ZONE), curEnd.plusMonths(2));
        assertThat(all).hasSize(2).allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(ReservationStatus.CANCELLED));
        // el aviso sale al confirmar: push "te quitaron el plan" al socio
        verify(pushService, atLeastOnce()).notifyUsers(eq(List.of(m.getId())), any());
    }
}
