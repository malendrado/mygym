package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Sends the "you've been added as a gym admin" email via Resend's REST API.
 * Best-effort: a failure here (missing key, Resend down, etc.) is logged and
 * swallowed — the admin was already added successfully in the DB, so a flaky
 * email must never roll back or fail that operation.
 */
@Service
public class AdminInviteEmailService {

    private static final Logger log = LoggerFactory.getLogger(AdminInviteEmailService.class);
    private static final String LOGIN_URL = "https://www.mygym.cl/login";

    private final RestClient restClient;
    private final String apiKey;
    private final String from;
    private final String template;
    private final String ownerTemplate;
    private final String demoTemplate;

    private final java.util.concurrent.Executor emailExecutor;

    public AdminInviteEmailService(
            RestClient.Builder restClientBuilder,
            @org.springframework.beans.factory.annotation.Qualifier("emailExecutor") java.util.concurrent.Executor emailExecutor,
            @Value("${app.resend.api-key}") String apiKey,
            @Value("${app.resend.from}") String from) {
        this.emailExecutor = emailExecutor;
        this.restClient = restClientBuilder.baseUrl("https://api.resend.com").build();
        this.apiKey = apiKey;
        this.from = from;
        this.template = loadTemplate("templates/email/admin-invite.html");
        this.ownerTemplate = loadTemplate("templates/email/gym-owner-invite.html");
        this.demoTemplate = loadTemplate("templates/email/demo-invite.html");
    }

    public void sendAdminInvite(Gym gym, AppUser admin) {
        send(template, "Ya tienes acceso a administrar " + gym.getName(), gym, admin);
    }

    // Variante más cálida, solo para el dueño de un gym RECIÉN CREADO — agrega el aviso de
    // completar los datos de la cuenta Pago Online. Un admin agregado después a un gym que ya
    // existe sigue recibiendo sendAdminInvite tal cual (no tiene sentido repetirle este aviso).
    public void sendGymOwnerInvite(Gym gym, AppUser owner) {
        send(ownerTemplate, "Bienvenido a mygym — ya puedes administrar " + gym.getName(), gym, owner);
    }

    // Misma mecánica que sendAdminInvite pero con la plantilla/copy de demo-invite.html — nunca
    // confundir los dos: este es un DEMO_ADMIN de solo-lectura, no un GYM_ADMIN real.
    public void sendDemoInvite(Gym gym, AppUser demoAdmin) {
        send(demoTemplate, "Tu demo de mygym está lista", gym, demoAdmin);
    }

    private void send(String htmlTemplate, String subject, Gym gym, AppUser admin) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("RESEND_API_KEY no configurada — se omite el email de invitación para {}", LogMask.email(admin.getEmail()));
            return;
        }
        String html = renderHtml(htmlTemplate, gym, admin);
        String to = admin.getEmail();
        String gymName = gym.getName();
        Map<String, Object> body = Map.of(
                "from", from,
                "to", java.util.List.of(to),
                "subject", subject,
                "html", html);
        // La llamada a Resend sale del hilo de la request (puede estar dentro de una transacción con conexión de la BD).
        emailExecutor.execute(() -> {
            try {
                restClient
                        .post()
                        .uri("/emails")
                        .header("Authorization", "Bearer " + apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();
                log.info("Email de invitación enviado a {} para el gimnasio {}", LogMask.email(to), gymName);
            } catch (RestClientException e) {
                log.error("Falló el envío del email de invitación a {}: {}", LogMask.email(to), e.getMessage());
            }
        });
    }

    private String renderHtml(String htmlTemplate, Gym gym, AppUser admin) {
        String themeColor = gym.getThemeColor() != null ? gym.getThemeColor() : GymPalette.defaultHex();
        String contrast = GymPalette.contrastFor(themeColor);

        return htmlTemplate
                .replace("{{GYM_NAME}}", escapeHtml(gym.getName()))
                .replace("{{ADMIN_NAME}}", escapeHtml(admin.getName()))
                .replace("{{ADMIN_EMAIL}}", escapeHtml(admin.getEmail()))
                .replace("{{INVITER_NAME}}", "el equipo de mygym")
                .replace("{{INVITE_URL}}", LOGIN_URL)
                .replace("{{THEME_COLOR}}", themeColor)
                .replace("{{THEME_CONTRAST}}", contrast)
                .replace("{{LOGO_BADGE_INNER}}", logoBadgeInner(gym));
    }

    // Ver el mismo comentario/fix en MemberLifecycleEmailService. Se probaron dos formatos de
    // gym.logoSvg como <img>/<svg> inline en el email — ninguno funciona en Gmail: el <svg> crudo
    // no lo renderiza casi ningún cliente (solo Apple Mail), y un <img src="data:..."> tampoco:
    // Gmail directamente no muestra imágenes data: (base64) embebidas, sea cual sea el formato
    // (confirmado con un envío real, no solo en teoría). Mostrar el logo real en el email
    // requeriría mandarlo como adjunto embebido (content-id) en vez de data URI — se dejó afuera
    // a propósito (decisión explícita del usuario, ver conversación) por el trabajo/riesgo extra.
    // Se usa siempre el mismo fallback de inicial acá — el logo real se sigue viendo en la web/app.
    private String logoBadgeInner(Gym gym) {
        return escapeHtml(GymPalette.initialOf(gym.getName()));
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String loadTemplate(String classpathLocation) {
        try {
            byte[] bytes =
                    StreamUtils.copyToByteArray(new ClassPathResource(classpathLocation).getInputStream());
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cargar la plantilla de email " + classpathLocation, e);
        }
    }
}
