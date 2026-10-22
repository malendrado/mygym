package com.cortesdev.mygym.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymContractAcceptance;
import com.cortesdev.mygym.models.GymContractCode;
import com.cortesdev.mygym.models.GymSubscription;
import com.cortesdev.mygym.models.MygymPlan;
import com.cortesdev.mygym.models.dto.GymContractDtos.CodeRequest;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymContractAcceptanceRepository;
import com.cortesdev.mygym.repositories.GymContractCodeRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.GymSubscriptionRepository;
import com.cortesdev.mygym.services.exception.InvalidSubscriptionException;
import com.cortesdev.mygym.services.exception.SubscriptionConflictException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Firma del contrato: codigo al correo, intentos, vencimiento, texto que no cambia entre el codigo y la firma, y la constancia. */
@ExtendWith(MockitoExtension.class)
class ContractServiceTest {

    private static final Long GYM_ID = 5L;
    private static final Long USER_ID = 9L;
    private static final String RUT = "12.345.678-5";

    @Mock private GymRepository gymRepository;
    @Mock private GymSubscriptionRepository subscriptionRepository;
    @Mock private GymSubscriptionService subscriptionService;
    @Mock private AppUserRepository appUserRepository;
    @Mock private GymContractAcceptanceRepository acceptanceRepository;
    @Mock private GymContractCodeRepository codeRepository;
    @Mock private MemberLifecycleEmailService emailService;
    @InjectMocks private ContractService service;

    private final Gym gym = Gym.builder().id(GYM_ID).name("XaloGym").build();
    private final AppUser admin = AppUser.builder().id(USER_ID).email("admin@xalogym.cl").name("Ana Admin").build();
    private final MygymPlan empieza =
            MygymPlan.builder().id(1L).code("EMPIEZA").name("Empieza").clientsLabel("Hasta 100 socios").priceClp(29_990).build();
    private GymSubscription sub;
    private final List<GymContractCode> savedCodes = new ArrayList<>();

    @BeforeEach
    void stubs() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "mygymEmail", "contacto@mygym.cl");
        sub = GymSubscription.builder()
                .gymId(GYM_ID).planId(1L).billingStatus(GymSubscription.NOT_STARTED).billingKind(GymSubscription.BILLING_COMPANY)
                .billingRut("76.123.456-7").billingName("Gimnasio SpA").billingAddress("Av. Siempre Viva 123")
                .billingGiro("Servicios deportivos").billingEmail("facturas@xalogym.cl").build();
        lenient().when(gymRepository.findById(GYM_ID)).thenReturn(Optional.of(gym));
        lenient().when(subscriptionRepository.findLockedByGymId(GYM_ID)).thenReturn(Optional.of(sub));
        lenient().when(subscriptionService.target(any(), any())).thenReturn(new GymSubscriptionService.Target(empieza, 29_990, false));
        lenient().when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(admin));
        lenient().when(acceptanceRepository.existsByGymIdAndContractVersion(any(), any())).thenReturn(false);
        lenient().when(acceptanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(codeRepository.save(any())).thenAnswer(inv -> {
            GymContractCode c = inv.getArgument(0);
            savedCodes.add(c);
            return c;
        });
        lenient().when(codeRepository.findFirstByUserIdOrderByIdDesc(USER_ID)).thenReturn(Optional.empty());
        lenient().when(codeRepository.findByGymIdAndUserIdAndUsedAtIsNull(GYM_ID, USER_ID)).thenReturn(List.of());
    }

    private static CodeRequest request(String capacity) {
        return new CodeRequest("Ana  Admin", RUT, capacity, true, true);
    }

    /** Pide el codigo y lo lee del correo que se habria enviado. */
    private String requestCode() {
        service.sendCode(GYM_ID, USER_ID, request("LEGAL_REP"));
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendNotice(any(), eq("admin@xalogym.cl"), anyString(), anyString(), anyString(), body.capture(), anyString(), anyString(), anyString());
        Matcher m = Pattern.compile("letter-spacing:6px;\">(\\d{6})<").matcher(body.getValue());
        assertTrue(m.find());
        return m.group(1);
    }

    private GymContractCode pending() {
        GymContractCode code = savedCodes.get(savedCodes.size() - 1);
        code.setId(1L);
        lenient().when(codeRepository.findFirstByGymIdAndUserIdAndUsedAtIsNullOrderByIdDesc(GYM_ID, USER_ID)).thenReturn(Optional.of(code));
        return code;
    }

    @Test
    void theViewLeavesMarkersForTheSignerDataAndFillsEverythingElse() {
        when(subscriptionRepository.findById(GYM_ID)).thenReturn(Optional.of(sub));

        String text = service.view(GYM_ID, USER_ID).text();

        assertTrue(text.contains("{{signerName}}") && text.contains("{{signerRut}}") && text.contains("{{signerCapacity}}"));
        assertFalse(text.contains("____"));
        assertTrue(text.contains("Gimnasio SpA") && text.contains("76.123.456-7") && text.contains("admin@xalogym.cl"));
    }

    @Test
    void withoutTheFullBillingDataTheCodeIsNotSent() {
        sub.setBillingGiro(null);

        InvalidSubscriptionException e = assertThrows(InvalidSubscriptionException.class, () -> service.sendCode(GYM_ID, USER_ID, request("LEGAL_REP")));

        assertTrue(e.getMessage().contains("datos del contrato y la factura"));
        verify(emailService, never()).sendNotice(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void validCodeSignsAndLeavesTheEvidence() {
        String code = requestCode();
        GymContractCode pending = pending();

        service.sign(GYM_ID, USER_ID, code, "190.1.2.3", "Mozilla/5.0");

        ArgumentCaptor<GymContractAcceptance> saved = ArgumentCaptor.forClass(GymContractAcceptance.class);
        verify(acceptanceRepository).save(saved.capture());
        GymContractAcceptance a = saved.getValue();
        assertEquals(ContractService.VERSION, a.getContractVersion());
        assertEquals("Ana Admin", a.getSignerName());
        assertEquals("12.345.678-5", a.getSignerRut());
        assertEquals("LEGAL_REP", a.getSignerCapacity());
        assertEquals("190.1.2.3", a.getIp());
        assertEquals(29_990, a.getNetPriceClp());
        assertTrue(a.isAdultDeclared() && a.isPowersDeclared());
        assertEquals(pending.getRenderedSha256(), a.getRenderedSha256());
        assertEquals(ContractService.sha256(a.getRenderedText()), a.getRenderedSha256());
        assertTrue(a.getRenderedText().contains("Gimnasio SpA") && a.getRenderedText().contains("$29.990") && a.getRenderedText().contains("Ana Admin"));
        assertNotNull(pending.getUsedAt());
        // La constancia sale al firmante y a mygym.
        verify(emailService, org.mockito.Mockito.times(2)).sendNotice(any(), anyString(), eq("Constancia de firma del contrato mygym"),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void wrongCodeIsCountedAndFiveWrongOnesBurnTheCode() {
        requestCode();
        GymContractCode pending = pending();

        for (int i = 1; i < ContractService.MAX_ATTEMPTS; i++) {
            InvalidSubscriptionException e = assertThrows(InvalidSubscriptionException.class, () -> service.sign(GYM_ID, USER_ID, "000000", null, null));
            assertTrue(e.getMessage().contains("no es correcto"));
        }
        assertEquals(ContractService.MAX_ATTEMPTS - 1, pending.getAttempts());
        assertThrows(InvalidSubscriptionException.class, () -> service.sign(GYM_ID, USER_ID, "000000", null, null));
        assertNotNull(pending.getUsedAt());
        verify(acceptanceRepository, never()).save(any());
    }

    @Test
    void expiredCodeIsRefused() {
        String code = requestCode();
        GymContractCode pending = pending();
        pending.setExpiresAt(Instant.now().minusSeconds(1));

        InvalidSubscriptionException e = assertThrows(InvalidSubscriptionException.class, () -> service.sign(GYM_ID, USER_ID, code, null, null));
        assertTrue(e.getMessage().contains("venció"));
        verify(acceptanceRepository, never()).save(any());
    }

    @Test
    void textThatChangedAfterTheCodeWasRequestedIsNotSigned() {
        String code = requestCode();
        GymContractCode pending = pending();
        sub.setBillingName("Otra Razon Social SpA");

        InvalidSubscriptionException e = assertThrows(InvalidSubscriptionException.class, () -> service.sign(GYM_ID, USER_ID, code, null, null));
        assertTrue(e.getMessage().contains("cambiaron"));
        assertNotNull(pending.getUsedAt());
        verify(acceptanceRepository, never()).save(any());
    }

    @Test
    void codeCannotSignWithoutHavingBeenRequested() {
        when(codeRepository.findFirstByGymIdAndUserIdAndUsedAtIsNullOrderByIdDesc(GYM_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(InvalidSubscriptionException.class, () -> service.sign(GYM_ID, USER_ID, "123456", null, null));
    }

    @Test
    void alreadySignedGymCannotSignTwice() {
        when(acceptanceRepository.existsByGymIdAndContractVersion(GYM_ID, ContractService.VERSION)).thenReturn(true);

        assertThrows(SubscriptionConflictException.class, () -> service.sendCode(GYM_ID, USER_ID, request("LEGAL_REP")));
        assertThrows(SubscriptionConflictException.class, () -> service.sign(GYM_ID, USER_ID, "123456", null, null));
    }

    @Test
    void capacityMustMatchTheKindOfBillingAndRutMustBeValid() {
        assertThrows(InvalidSubscriptionException.class, () -> service.sendCode(GYM_ID, USER_ID, request("PERSON_OWNER")));
        sub.setBillingKind(GymSubscription.BILLING_PERSON);
        assertThrows(InvalidSubscriptionException.class, () -> service.sendCode(GYM_ID, USER_ID, request("LEGAL_REP")));
        assertThrows(InvalidSubscriptionException.class,
                () -> service.sendCode(GYM_ID, USER_ID, new CodeRequest("Ana Admin", "12.345.678-9", "PERSON_OWNER", true, true)));
        verify(codeRepository, never()).save(any());
    }

    @Test
    void cannotSignWithoutPlanPriceOrInvoiceData() {
        sub.setBillingAddress(null);
        assertThrows(InvalidSubscriptionException.class, () -> service.sendCode(GYM_ID, USER_ID, request("LEGAL_REP")));

        sub.setBillingAddress("Av. Siempre Viva 123");
        when(subscriptionService.target(any(), any())).thenReturn(new GymSubscriptionService.Target(null, null, false));
        assertThrows(InvalidSubscriptionException.class, () -> service.sendCode(GYM_ID, USER_ID, request("LEGAL_REP")));
    }

    @Test
    void asksToWaitBetweenCodes() {
        GymContractCode recent = GymContractCode.builder().createdAt(Instant.now().minusSeconds(10)).build();
        when(codeRepository.findFirstByUserIdOrderByIdDesc(USER_ID)).thenReturn(Optional.of(recent));

        InvalidSubscriptionException e = assertThrows(InvalidSubscriptionException.class, () -> service.sendCode(GYM_ID, USER_ID, request("LEGAL_REP")));
        assertTrue(e.getMessage().contains("minuto"));
    }

    @Test
    void aDataFieldWithPlaceholderTextCannotInjectAnotherField() {
        sub.setBillingName("{{signerName}}");
        String code = requestCode();
        pending();

        service.sign(GYM_ID, USER_ID, code, null, null);

        ArgumentCaptor<GymContractAcceptance> saved = ArgumentCaptor.forClass(GymContractAcceptance.class);
        verify(acceptanceRepository).save(saved.capture());
        // El nombre del gimnasio queda literal: lo insertado no se vuelve a escanear en busca de campos.
        assertTrue(saved.getValue().getRenderedText().contains("y {{signerName}}, RUT 76.123.456-7"));
    }

    @Test
    void theCopyEscapesEverythingAndForbidsScripts() {
        GymContractAcceptance a = GymContractAcceptance.builder()
                .gymId(GYM_ID).contractVersion("v1").gymName("<script>alert(1)</script>").signerName("<img src=x onerror=alert(2)>")
                .signerRut(RUT).signerCapacity("LEGAL_REP").signerEmail("admin@xalogym.cl").signedAt(Instant.now())
                .renderedText("Texto <b>del</b> contrato").renderedSha256("abc").ip("1.2.3.4").userAgent("UA").build();
        when(acceptanceRepository.findFirstByGymIdOrderByIdDesc(GYM_ID)).thenReturn(Optional.of(a));

        String html = service.copyHtml(GYM_ID);

        assertTrue(html.contains("Content-Security-Policy"));
        assertFalse(html.contains("<script>") || html.contains("<img") || html.contains("<b>"));
        assertTrue(html.contains("&lt;script&gt;"));
    }

    @Test
    void summaryOfTellsWhetherEscalaNeedsTheCertificate() {
        MygymPlan escala = MygymPlan.builder().id(3L).code("ESCALA").name("Escala").build();
        assertTrue(ContractService.summaryOf(null, sub, escala).certificateNeeded());
        sub.setBillingKind(GymSubscription.BILLING_PERSON);
        assertFalse(ContractService.summaryOf(null, sub, escala).certificateNeeded());
        assertFalse(ContractService.summaryOf(null, sub, empieza).signed());
    }
}
