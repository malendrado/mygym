package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.MygymSettings;
import com.cortesdev.mygym.models.dto.MygymSettingsDtos.PublicContact;
import com.cortesdev.mygym.models.dto.MygymSettingsDtos.Request;
import com.cortesdev.mygym.models.dto.MygymSettingsDtos.Response;
import com.cortesdev.mygym.repositories.MygymSettingsRepository;
import com.cortesdev.mygym.services.exception.InvalidSubscriptionException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Única fuente de los datos de mygym (contacto, empresa del contrato, cuenta bancaria). Lo guardado en el panel de super admin manda;
 * si un campo está vacío se usa el valor de respaldo (variable de entorno o dato histórico), así un campo en blanco nunca deja un
 * mensaje o un contrato sin dato. Se lee mucho (cada estado de gimnasio y cada vista de suscripción) y cambia casi nunca: se guarda
 * 30 s en memoria y se invalida al guardar (hay una sola instancia de la API).
 */
@Service
@RequiredArgsConstructor
public class MygymSettingsService {

    static final String DEFAULT_COMPANY_NAME = "INFORMÁTICA LUIS CORTÉS ÁLVAREZ E.I.R.L.";
    static final String DEFAULT_COMPANY_RUT = "76.831.567-1";
    static final String DEFAULT_REP_NAME = "Luis René Cortés Álvarez";
    static final String DEFAULT_REP_RUT = "16.302.718-6";
    static final String DEFAULT_ADDRESS = "La Unión 102, comuna de Puchuncaví";

    private static final long TTL_NANOS = TimeUnit.SECONDS.toNanos(30);

    public record Company(String name, String rut, String legalRepName, String legalRepRut, String address) {}

    public record Bank(String holder, String rut, String bank, String accountType, String accountNumber, String email) {}

    private record Cached(MygymSettings row, long expiresAt) {}

    private final MygymSettingsRepository repository;

    @Value("${app.contact.whatsapp:56964641042}")
    private String fallbackWhatsapp;

    @Value("${app.contact.email:contacto@mygym.cl}")
    private String fallbackEmail;

    @Value("${app.mygym-bank.holder:}")
    private String fallbackBankHolder;

    @Value("${app.mygym-bank.rut:}")
    private String fallbackBankRut;

    @Value("${app.mygym-bank.bank:}")
    private String fallbackBankName;

    @Value("${app.mygym-bank.account-type:}")
    private String fallbackBankAccountType;

    @Value("${app.mygym-bank.account-number:}")
    private String fallbackBankAccountNumber;

    @Value("${app.mygym-bank.email:}")
    private String fallbackBankEmail;

    private volatile Cached cached;

    // ---- Lectura para el resto del servidor ----------------------------------------------------------------------------

    public String whatsapp() {
        return pick(row().getWhatsapp(), fallbackWhatsapp);
    }

    public String email() {
        return pick(row().getContactEmail(), fallbackEmail);
    }

    public PublicContact publicContact() {
        return new PublicContact(whatsapp(), email());
    }

    public Company company() {
        MygymSettings r = row();
        return new Company(
                pick(r.getCompanyName(), DEFAULT_COMPANY_NAME),
                pick(r.getCompanyRut(), DEFAULT_COMPANY_RUT),
                pick(r.getLegalRepName(), DEFAULT_REP_NAME),
                pick(r.getLegalRepRut(), DEFAULT_REP_RUT),
                pick(r.getCompanyAddress(), DEFAULT_ADDRESS));
    }

    public Bank bank() {
        MygymSettings r = row();
        return new Bank(
                pick(r.getBankHolder(), fallbackBankHolder),
                pick(r.getBankRut(), fallbackBankRut),
                pick(r.getBankName(), fallbackBankName),
                pick(r.getBankAccountType(), fallbackBankAccountType),
                pick(r.getBankAccountNumber(), fallbackBankAccountNumber),
                pick(pick(r.getBankEmail(), fallbackBankEmail), email()));
    }

    // ---- Panel de super admin ------------------------------------------------------------------------------------------

    public Response current() {
        MygymSettings r = row();
        Company c = company();
        Bank b = bank();
        return new Response(
                whatsapp(), email(), c.name(), c.rut(), c.legalRepName(), c.legalRepRut(), c.address(),
                b.holder(), b.rut(), b.bank(), b.accountType(), b.accountNumber(), b.email(), r.getUpdatedAt());
    }

    @Transactional
    public Response update(Request request, Long userId) {
        // Se valida todo antes de tocar la fila: un RUT malo no deja datos a medio guardar.
        String companyRut = rut(request.companyRut(), "El RUT de la empresa");
        String legalRepRut = rut(request.legalRepRut(), "El RUT del representante legal");
        String bankRut = isBlank(request.bankRut()) ? null : rut(request.bankRut(), "El RUT de la cuenta");
        MygymSettings row = repository.findById(MygymSettings.ID).orElseGet(() -> MygymSettings.builder().id(MygymSettings.ID).build());
        row.setWhatsapp(request.whatsapp().trim());
        row.setContactEmail(request.contactEmail().trim());
        row.setCompanyName(request.companyName().trim());
        row.setCompanyRut(companyRut);
        row.setLegalRepName(request.legalRepName().trim());
        row.setLegalRepRut(legalRepRut);
        row.setCompanyAddress(request.companyAddress().trim());
        row.setBankHolder(blankToNull(request.bankHolder()));
        row.setBankRut(bankRut);
        row.setBankName(blankToNull(request.bankName()));
        row.setBankAccountType(blankToNull(request.bankAccountType()));
        row.setBankAccountNumber(blankToNull(request.bankAccountNumber()));
        row.setBankEmail(blankToNull(request.bankEmail()));
        row.setUpdatedAt(Instant.now());
        row.setUpdatedBy(userId);
        repository.save(row);
        cached = null;
        return current();
    }

    // ---- Internos ------------------------------------------------------------------------------------------------------

    private MygymSettings row() {
        long now = System.nanoTime();
        Cached c = cached;
        if (c != null && now - c.expiresAt() < 0) {
            return c.row();
        }
        MygymSettings fresh = repository.findById(MygymSettings.ID).orElseGet(() -> MygymSettings.builder().id(MygymSettings.ID).build());
        cached = new Cached(fresh, now + TTL_NANOS);
        return fresh;
    }

    private static String rut(String value, String what) {
        if (!RutValidator.isValid(value)) {
            throw new InvalidSubscriptionException(what + " no es válido: revisa el número y el dígito verificador.");
        }
        return RutValidator.format(value);
    }

    private static String pick(String stored, String fallback) {
        return isBlank(stored) ? fallback : stored.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
