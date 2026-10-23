package com.cortesdev.mygym.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cortesdev.mygym.repositories.MygymSettingsRepository;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

/** {@link MygymSettingsService} real, sin nada guardado: entrega los valores de respaldo de siempre (WhatsApp, correo y datos de la empresa). */
public final class TestMygymSettings {

    private TestMygymSettings() {}

    public static MygymSettingsService defaults() {
        MygymSettingsRepository repository = mock(MygymSettingsRepository.class);
        when(repository.findById(any())).thenReturn(Optional.empty());
        MygymSettingsService service = new MygymSettingsService(repository);
        ReflectionTestUtils.setField(service, "fallbackWhatsapp", "56964641042");
        ReflectionTestUtils.setField(service, "fallbackEmail", "contacto@mygym.cl");
        for (String field : new String[] {"fallbackBankHolder", "fallbackBankRut", "fallbackBankName", "fallbackBankAccountType", "fallbackBankAccountNumber", "fallbackBankEmail"}) {
            ReflectionTestUtils.setField(service, field, "");
        }
        return service;
    }
}
