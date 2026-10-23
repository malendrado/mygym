package com.cortesdev.mygym.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.cortesdev.mygym.models.MygymSettings;
import com.cortesdev.mygym.models.dto.MygymSettingsDtos.Request;
import com.cortesdev.mygym.repositories.MygymSettingsRepository;
import com.cortesdev.mygym.services.exception.InvalidSubscriptionException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MygymSettingsServiceTest {

    @Mock private MygymSettingsRepository repository;

    private MygymSettingsService service;
    private MygymSettings row;

    @BeforeEach
    void setUp() {
        row = MygymSettings.builder().id(MygymSettings.ID).build();
        lenient().when(repository.findById(MygymSettings.ID)).thenReturn(Optional.of(row));
        lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new MygymSettingsService(repository);
        ReflectionTestUtils.setField(service, "fallbackWhatsapp", "56964641042");
        ReflectionTestUtils.setField(service, "fallbackEmail", "contacto@mygym.cl");
        ReflectionTestUtils.setField(service, "fallbackBankHolder", "Titular desde variable");
        for (String f : new String[] {"fallbackBankRut", "fallbackBankName", "fallbackBankAccountType", "fallbackBankAccountNumber", "fallbackBankEmail"}) {
            ReflectionTestUtils.setField(service, f, "");
        }
    }

    private static Request request(String whatsapp, String email, String companyRut) {
        return new Request(whatsapp, email, "Mygym SpA", companyRut, "Ana Rep", "16.302.718-6", "Av. Brasil 1, Valparaíso",
                "Mygym SpA", "76831567-1", "Banco Estado", "Cuenta Corriente", "123456", "pagos@mygym.cl");
    }

    @Test
    void withNothingSavedTheFallbackValuesOfAlwaysAreUsed() {
        assertEquals("56964641042", service.whatsapp());
        assertEquals("contacto@mygym.cl", service.email());
        MygymSettingsService.Company company = service.company();
        assertEquals("INFORMÁTICA LUIS CORTÉS ÁLVAREZ E.I.R.L.", company.name());
        assertEquals("76.831.567-1", company.rut());
        assertEquals("La Unión 102, comuna de Puchuncaví", company.address());
        // La cuenta cae a las variables de entorno y su correo, al de contacto
        assertEquals("Titular desde variable", service.bank().holder());
        assertEquals("contacto@mygym.cl", service.bank().email());
    }

    @Test
    void savingStoresTheDataNormalizedAndEveryoneReadsItFromThenOn() {
        service.update(request(" 56912345678 ", "nuevo@mygym.cl", "76831567-1"), 7L);

        assertEquals("56912345678", row.getWhatsapp());
        assertEquals("76.831.567-1", row.getCompanyRut());
        assertEquals(7L, row.getUpdatedBy());
        assertEquals("56912345678", service.whatsapp());
        assertEquals("nuevo@mygym.cl", service.email());
        assertEquals("Mygym SpA", service.company().name());
        assertEquals("pagos@mygym.cl", service.bank().email());
        assertEquals("Mygym SpA", service.bank().holder());
    }

    @Test
    void anInvalidRutIsRejectedAndNothingIsSaved() {
        assertThrows(InvalidSubscriptionException.class, () -> service.update(request("56912345678", "a@mygym.cl", "76831567-9"), 7L));
        assertNull(row.getWhatsapp());
        verify(repository, times(0)).save(any());
    }

    @Test
    void blankOptionalBankFieldsGoBackToTheFallbackInsteadOfLeavingTheGymWithoutData() {
        Request noBank = new Request("56912345678", "a@mygym.cl", "Mygym SpA", "76831567-1", "Ana Rep", "16302718-6", "Dirección", "", "", " ", null, null, null);
        service.update(noBank, 7L);

        assertNull(row.getBankName());
        assertEquals("Titular desde variable", service.bank().holder());
    }

    @Test
    void theValueReadIsCachedButASaveIsSeenAtOnce() {
        service.email();
        service.email();
        service.whatsapp();
        verify(repository, times(1)).findById(MygymSettings.ID);

        service.update(request("56912345678", "otro@mygym.cl", "76831567-1"), 7L);

        assertEquals("otro@mygym.cl", service.email());
    }
}
