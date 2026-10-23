package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymContractAcceptance;
import com.cortesdev.mygym.models.GymContractCode;
import com.cortesdev.mygym.models.GymSubscription;
import com.cortesdev.mygym.models.MygymPlan;
import com.cortesdev.mygym.models.dto.GymContractDtos.CodeRequest;
import com.cortesdev.mygym.models.dto.GymContractDtos.CodeResponse;
import com.cortesdev.mygym.models.dto.GymContractDtos.ContractSummary;
import com.cortesdev.mygym.models.dto.GymContractDtos.ContractView;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymContractAcceptanceRepository;
import com.cortesdev.mygym.repositories.GymContractCodeRepository;
import com.cortesdev.mygym.repositories.GymRepository;
import com.cortesdev.mygym.repositories.GymSubscriptionRepository;
import com.cortesdev.mygym.services.exception.GymNotFoundException;
import com.cortesdev.mygym.services.exception.InvalidSubscriptionException;
import com.cortesdev.mygym.services.exception.SubscriptionConflictException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.HtmlUtils;

/**
 * Firma electronica simple (Ley 19.799) del contrato de servicios por el administrador del gimnasio: pide un codigo de 6 digitos
 * que llega al correo de su cuenta y deja una constancia inmutable con el texto exacto, quien firmo, cuando y desde donde.
 * Se exige antes de cada pago del gimnasio a mygym mientras no haya firmado la version vigente (ver GymSubscriptionService).
 * Plan: docs/plans/2026-10-21-firma-contrato.md
 *
 * <p>shortcut: los codigos usados o vencidos no se purgan (son filas minimas, una por intento y con tope por hora); agregarlo al
 * ExpiredRowsPurgeJob si algun dia crecen.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ContractService {

    private static final Logger log = LoggerFactory.getLogger(ContractService.class);

    /** Version vigente del texto (resources/contracts/contrato-v1.txt). Si el texto cambia, sube la version y todos vuelven a firmar. */
    public static final String VERSION = "v1";

    static final Duration CODE_TTL = Duration.ofMinutes(10);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    static final int MAX_CODES_PER_HOUR = 5;
    static final int MAX_ATTEMPTS = 5;

    private static final ZoneId CHILE = ZoneId.of("America/Santiago");
    private static final Locale ES_CL = Locale.forLanguageTag("es-CL");
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", ES_CL);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", ES_CL);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");
    private static final String TEMPLATE = loadTemplate();
    private static final String LOGIN_URL = "https://www.mygym.cl/login";

    private final GymRepository gymRepository;
    private final GymSubscriptionRepository subscriptionRepository;
    private final GymSubscriptionService subscriptionService;
    private final AppUserRepository appUserRepository;
    private final GymContractAcceptanceRepository acceptanceRepository;
    private final GymContractCodeRepository codeRepository;
    private final MemberLifecycleEmailService emailService;

    private final MygymSettingsService mygymSettings;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    /** Datos que se rellenan en el texto. Los del firmante van en blanco en la vista previa. */
    private record Fields(
            String date, String gymName, String gymRut, String gymAddress, String signerName, String signerRut,
            String capacity, String signerEmail, String planName, String clientsLabel, String netPrice,
            MygymSettingsService.Company company, String contactEmail) {}

    // ---- Lectura -------------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ContractView view(Long gymId, Long userId) {
        GymSubscription sub = subscriptionRepository.findById(gymId).orElse(null);
        AppUser user = appUserRepository.findById(userId).orElse(null);
        GymContractAcceptance accepted = acceptanceRepository.findFirstByGymIdAndContractVersionOrderByIdDesc(gymId, VERSION).orElse(null);
        MygymPlan plan = sub == null ? null : subscriptionService.target(sub, GymBilling.today()).plan();
        String blocked = sub == null ? noPlan() : blockedReason(sub);
        String text = accepted != null ? accepted.getRenderedText()
                : blocked != null ? null
                // Los datos de quien firma quedan como marcadores: el front los reemplaza mientras se escriben; el texto que se firma lo arma sendCode.
                : render(fields(sub, GymBilling.today(), "{{signerName}}", "{{signerRut}}", "{{signerCapacity}}", user == null ? "" : user.getEmail()));
        return new ContractView(
                VERSION,
                accepted != null,
                accepted == null ? null : accepted.getSignedAt(),
                accepted == null ? null : accepted.getSignerName(),
                accepted == null ? null : accepted.getSignerCapacity(),
                text,
                accepted != null ? null : blocked,
                user == null ? null : mask(user.getEmail()),
                sub != null && certificateNeeded(plan, sub));
    }

    /** Resumen para la respuesta de suscripcion (panel del gimnasio y ficha del super-admin). */
    @Transactional(readOnly = true)
    public ContractSummary summary(GymSubscription sub, MygymPlan plan) {
        return summaryOf(acceptanceRepository.findFirstByGymIdAndContractVersionOrderByIdDesc(sub.getGymId(), VERSION).orElse(null), sub, plan);
    }

    /** Estatico para que GymSubscriptionService lo arme sin depender de este servicio (este ya depende de aquel). */
    public static ContractSummary summaryOf(GymContractAcceptance accepted, GymSubscription sub, MygymPlan plan) {
        return new ContractSummary(
                !sub.isExempt() && plan != null,
                accepted != null,
                VERSION,
                accepted == null ? null : accepted.getSignedAt(),
                accepted == null ? null : accepted.getSignerName(),
                accepted == null ? null : accepted.getSignerCapacity(),
                certificateNeeded(plan, sub));
    }

    /** Constancia descargable: el contrato firmado y la evidencia, como una pagina HTML simple. */
    @Transactional(readOnly = true)
    public String copyHtml(Long gymId) {
        GymContractAcceptance a = acceptanceRepository
                .findFirstByGymIdOrderByIdDesc(gymId)
                .orElseThrow(() -> new SubscriptionConflictException("Este gimnasio todavía no firmó el contrato."));
        // Se abre desde la app (misma origen): la politica prohibe scripts y todo lo externo, aunque el contenido ya va escapado.
        return "<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\">"
                + "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; style-src 'unsafe-inline'\">"
                + "<title>Constancia de firma — " + esc(a.getGymName())
                + "</title></head><body style=\"font-family:Arial,sans-serif;max-width:760px;margin:24px auto;padding:0 16px;color:#111\">"
                + proofBlock(a) + "<hr><div style=\"white-space:pre-wrap;font-size:13px;line-height:1.5\">" + esc(a.getRenderedText())
                + "</div></body></html>";
    }

    // ---- Firma ---------------------------------------------------------------------------------------------------------

    public CodeResponse sendCode(Long gymId, Long userId, CodeRequest request) {
        Gym gym = gymRepository.findById(gymId).orElseThrow(() -> new GymNotFoundException(gymId));
        GymSubscription sub = subscriptionRepository.findLockedByGymId(gymId).orElseThrow(() -> new InvalidSubscriptionException(noPlan()));
        requireNotSigned(gymId);
        String blocked = blockedReason(sub);
        if (blocked != null) {
            throw new InvalidSubscriptionException(blocked);
        }
        AppUser user = appUserRepository.findById(userId).orElseThrow(() -> new InvalidSubscriptionException("No encontramos tu usuario."));
        String capacity = validCapacity(sub, request.capacity());
        if (!RutValidator.isValid(request.signerRut())) {
            throw new InvalidSubscriptionException("El RUT del firmante no es válido: revisa el número y el dígito verificador.");
        }
        String rut = RutValidator.format(request.signerRut());
        String name = request.signerName().trim().replaceAll("\\s+", " ");

        Instant now = Instant.now();
        codeRepository.findFirstByUserIdOrderByIdDesc(userId).ifPresent(last -> {
            if (last.getCreatedAt().isAfter(now.minus(RESEND_COOLDOWN))) {
                throw new InvalidSubscriptionException("Espera un minuto antes de pedir otro código.");
            }
        });
        if (codeRepository.countByUserIdAndCreatedAtAfter(userId, now.minus(Duration.ofHours(1))) >= MAX_CODES_PER_HOUR) {
            throw new InvalidSubscriptionException("Pediste demasiados códigos. Intenta de nuevo en una hora.");
        }
        // Un solo codigo vigente por usuario: pedir otro invalida el anterior.
        codeRepository.findByGymIdAndUserIdAndUsedAtIsNull(gymId, userId).forEach(old -> old.setUsedAt(now));

        String code = String.format("%06d", random.nextInt(1_000_000));
        String text = render(fields(sub, now.atZone(CHILE).toLocalDate(), name, rut, label(capacity), user.getEmail()));
        codeRepository.save(GymContractCode.builder()
                .gymId(gymId).userId(userId).codeHash(encoder.encode(code)).renderedSha256(sha256(text))
                .signerName(name).signerRut(rut).signerCapacity(capacity)
                .expiresAt(now.plus(CODE_TTL)).attempts(0).createdAt(now).build());

        afterCommit(() -> emailService.sendNotice(
                gym, user.getEmail(), "Tu código para firmar el contrato de mygym", "Contrato",
                "Tu código de firma: " + code,
                "<p style=\"margin:0 0 12px;\">Usa este código para firmar el contrato de servicios de <strong>" + esc(gym.getName())
                        + "</strong>:</p><p style=\"margin:0 0 12px;font-size:32px;font-weight:700;letter-spacing:6px;\">" + code
                        + "</p><p style=\"margin:0;\">Vence en 10 minutos. Si no lo pediste tú, ignora este correo y no lo compartas con nadie.</p>",
                "Volver a mygym", LOGIN_URL, "Recibiste este correo porque administras " + esc(gym.getName()) + " en mygym."));
        return new CodeResponse(mask(user.getEmail()));
    }

    /** Un codigo malo no deshace la transaccion: el intento fallido tiene que quedar contado. */
    @Transactional(noRollbackFor = InvalidSubscriptionException.class)
    public ContractSummary sign(Long gymId, Long userId, String code, String ip, String userAgent) {
        Gym gym = gymRepository.findById(gymId).orElseThrow(() -> new GymNotFoundException(gymId));
        GymSubscription sub = subscriptionRepository.findLockedByGymId(gymId).orElseThrow(() -> new InvalidSubscriptionException(noPlan()));
        requireNotSigned(gymId);
        GymContractCode pending = codeRepository.findFirstByGymIdAndUserIdAndUsedAtIsNullOrderByIdDesc(gymId, userId)
                .orElseThrow(() -> new InvalidSubscriptionException("Pide un código para firmar."));
        Instant now = Instant.now();
        if (now.isAfter(pending.getExpiresAt())) {
            pending.setUsedAt(now);
            throw new InvalidSubscriptionException("El código venció: pide uno nuevo.");
        }
        if (!encoder.matches(code, pending.getCodeHash())) {
            pending.setAttempts(pending.getAttempts() + 1);
            if (pending.getAttempts() >= MAX_ATTEMPTS) {
                pending.setUsedAt(now);
                throw new InvalidSubscriptionException("Demasiados intentos con ese código: pide uno nuevo.");
            }
            throw new InvalidSubscriptionException("El código no es correcto. Te quedan " + (MAX_ATTEMPTS - pending.getAttempts()) + " intentos.");
        }
        AppUser user = appUserRepository.findById(userId).orElseThrow(() -> new InvalidSubscriptionException("No encontramos tu usuario."));
        // Se arma de nuevo el texto del dia en que se pidio el codigo: si el plan, el precio o la factura cambiaron, no se firma otro texto.
        String text = blockedReason(sub) != null ? null : render(fields(sub, pending.getCreatedAt().atZone(CHILE).toLocalDate(),
                pending.getSignerName(), pending.getSignerRut(), label(pending.getSignerCapacity()), user.getEmail()));
        if (text == null || !sha256(text).equals(pending.getRenderedSha256())) {
            pending.setUsedAt(now);
            throw new InvalidSubscriptionException("Los datos del contrato cambiaron: pide un código nuevo para firmar el texto actualizado.");
        }
        pending.setUsedAt(now);
        GymSubscriptionService.Target target = subscriptionService.target(sub, GymBilling.today());
        GymContractAcceptance accepted = acceptanceRepository.save(GymContractAcceptance.builder()
                .gymId(gymId).contractVersion(VERSION).renderedText(text).renderedSha256(pending.getRenderedSha256())
                .signerUserId(userId).signerEmail(user.getEmail()).signerName(pending.getSignerName())
                .signerRut(pending.getSignerRut()).signerCapacity(pending.getSignerCapacity())
                .adultDeclared(true).powersDeclared(true)
                .gymName(sub.getBillingName()).gymRut(sub.getBillingRut()).billingKind(billingKind(sub))
                .planCode(target.plan().getCode()).netPriceClp(target.netClp())
                .codeSentAt(pending.getCreatedAt()).signedAt(now).ip(cut(ip, 64)).userAgent(cut(userAgent, 500)).build());
        log.info("Contrato {} firmado por el gimnasio {} (usuario {})", VERSION, gymId, userId);
        afterCommit(() -> sendReceipt(gym, accepted));
        return summary(sub, target.plan());
    }

    /** Sin firma de la version vigente no se puede pagar: lo llaman el pago con Flow y el aviso de transferencia. */
    @Transactional(readOnly = true)
    public void requireSigned(Long gymId) {
        if (!acceptanceRepository.existsByGymIdAndContractVersion(gymId, VERSION)) {
            throw new SubscriptionConflictException("Antes de pagar firma el contrato de servicios.");
        }
    }

    // ---- Internos ------------------------------------------------------------------------------------------------------

    private void requireNotSigned(Long gymId) {
        if (acceptanceRepository.existsByGymIdAndContractVersion(gymId, VERSION)) {
            throw new SubscriptionConflictException("Este gimnasio ya firmó el contrato.");
        }
    }

    /** Motivo por el que todavia no se puede firmar (faltan plan, precio o datos de factura), o null si esta listo. */
    private String noPlan() {
        return "Todavía no tienes un plan asignado: escríbenos a " + mygymSettings.email() + ".";
    }

    private String blockedReason(GymSubscription sub) {
        GymSubscriptionService.Target target = subscriptionService.target(sub, GymBilling.today());
        if (target.plan() == null) {
            return noPlan();
        }
        if (target.netClp() == null) {
            return "Tu plan es a medida y todavía no tiene precio: escríbenos a " + mygymSettings.email() + ".";
        }
        if (isBlank(sub.getBillingRut()) || isBlank(sub.getBillingName()) || isBlank(sub.getBillingGiro())
                || isBlank(sub.getBillingAddress()) || isBlank(sub.getBillingEmail())) {
            return "Completa primero los datos del contrato y la factura (RUT, razón social o nombre, giro, dirección y correo).";
        }
        return null;
    }

    private Fields fields(GymSubscription sub, LocalDate date, String signerName, String signerRut, String capacity, String signerEmail) {
        GymSubscriptionService.Target target = subscriptionService.target(sub, GymBilling.today());
        return new Fields(
                LONG_DATE.format(date), sub.getBillingName(), sub.getBillingRut(), sub.getBillingAddress(), signerName, signerRut,
                capacity, signerEmail, target.plan().getName(), target.plan().getClientsLabel(),
                "$" + NumberFormat.getIntegerInstance(ES_CL).format(target.netClp()),
                mygymSettings.company(), mygymSettings.email());
    }

    /** Un solo recorrido: lo que se inserta no se vuelve a escanear, asi un nombre con "{{...}}" no inyecta otro campo. */
    private static String render(Fields f) {
        Map<String, String> values = Map.ofEntries(
                Map.entry("date", f.date()), Map.entry("gymName", f.gymName()), Map.entry("gymRut", f.gymRut()),
                Map.entry("gymAddress", f.gymAddress()), Map.entry("signerName", f.signerName()), Map.entry("signerRut", f.signerRut()),
                Map.entry("signerCapacity", f.capacity()), Map.entry("signerEmail", f.signerEmail()), Map.entry("planName", f.planName()),
                Map.entry("clientsLabel", f.clientsLabel()), Map.entry("netPrice", f.netPrice()),
                Map.entry("companyName", f.company().name()), Map.entry("companyRut", f.company().rut()),
                Map.entry("legalRepName", f.company().legalRepName()), Map.entry("legalRepRut", f.company().legalRepRut()),
                Map.entry("companyAddress", f.company().address()), Map.entry("contactEmail", f.contactEmail()));
        Matcher m = PLACEHOLDER.matcher(TEMPLATE);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(values.getOrDefault(m.group(1), "")));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Titular solo en factura de persona natural; representante legal solo en empresa; apoderado en ambas. */
    private static String validCapacity(GymSubscription sub, String capacity) {
        boolean person = GymSubscription.BILLING_PERSON.equals(sub.getBillingKind());
        boolean ok = GymContractAcceptance.ATTORNEY.equals(capacity)
                || (person ? GymContractAcceptance.PERSON_OWNER : GymContractAcceptance.LEGAL_REP).equals(capacity);
        if (!ok) {
            throw new InvalidSubscriptionException(person
                    ? "Para una persona natural la calidad es titular o apoderado."
                    : "Para una empresa la calidad es representante legal o apoderado.");
        }
        return capacity;
    }

    private static String label(String capacity) {
        return switch (capacity) {
            case GymContractAcceptance.PERSON_OWNER -> "en calidad de titular";
            case GymContractAcceptance.LEGAL_REP -> "en calidad de representante legal";
            default -> "en calidad de apoderado";
        };
    }

    /** El plan Escala facturado a una empresa pide ademas el certificado de vigencia (se envia por correo, fuera del sistema). */
    private static boolean certificateNeeded(MygymPlan plan, GymSubscription sub) {
        return plan != null && "ESCALA".equals(plan.getCode()) && !GymSubscription.BILLING_PERSON.equals(sub.getBillingKind());
    }

    private void sendReceipt(Gym gym, GymContractAcceptance a) {
        String body = "<p style=\"margin:0 0 12px;\">Quedó firmado el contrato de servicios de <strong>" + esc(a.getGymName())
                + "</strong>. Guarda este correo: es tu constancia.</p>" + proofBlock(a)
                + "<div style=\"white-space:pre-wrap;font-size:12px;line-height:1.5;margin-top:12px;\">" + esc(a.getRenderedText()) + "</div>";
        for (String to : new String[] {a.getSignerEmail(), mygymSettings.email()}) {
            emailService.sendNotice(gym, to, "Constancia de firma del contrato mygym", "Contrato", "Contrato firmado", body,
                    "Volver a mygym", LOGIN_URL, "Constancia de firma electrónica simple (Ley N° 19.799).");
        }
    }

    private static String proofBlock(GymContractAcceptance a) {
        return "<p style=\"margin:0 0 12px;\"><strong>Constancia de firma electrónica simple (Ley N° 19.799)</strong><br>Firmante: "
                + esc(a.getSignerName()) + ", RUT " + esc(a.getSignerRut()) + ", " + esc(label(a.getSignerCapacity()))
                + "<br>Correo validado con código: " + esc(a.getSignerEmail())
                + "<br>Fecha y hora (Chile): " + STAMP.format(a.getSignedAt().atZone(CHILE))
                + "<br>IP: " + esc(a.getIp() == null ? "-" : a.getIp()) + "<br>Navegador: " + esc(a.getUserAgent() == null ? "-" : a.getUserAgent())
                + "<br>Versión del contrato: " + esc(a.getContractVersion()) + "<br>Huella SHA-256 del texto: " + esc(a.getRenderedSha256()) + "</p>";
    }

    /** El correo sale solo si la transaccion se confirma (un error al guardar no debe dejar un correo falso en la bandeja). */
    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private static String billingKind(GymSubscription sub) {
        return sub.getBillingKind() == null ? GymSubscription.BILLING_COMPANY : sub.getBillingKind();
    }

    private static String mask(String email) {
        if (email == null || !email.contains("@")) {
            return "";
        }
        return email.charAt(0) + "***" + email.substring(email.indexOf('@'));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String cut(String value, int max) {
        return value == null ? null : value.length() > max ? value.substring(0, max) : value;
    }

    private static String esc(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String loadTemplate() {
        try {
            return new String(new ClassPathResource("contracts/contrato-" + VERSION + ".txt").getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cargar el texto del contrato " + VERSION, e);
        }
    }
}
