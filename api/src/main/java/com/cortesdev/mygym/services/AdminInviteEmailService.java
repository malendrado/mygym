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
    private final String demoTemplate;

    public AdminInviteEmailService(
            RestClient.Builder restClientBuilder,
            @Value("${app.resend.api-key}") String apiKey,
            @Value("${app.resend.from}") String from) {
        this.restClient = restClientBuilder.baseUrl("https://api.resend.com").build();
        this.apiKey = apiKey;
        this.from = from;
        this.template = loadTemplate("templates/email/admin-invite.html");
        this.demoTemplate = loadTemplate("templates/email/demo-invite.html");
    }

    public void sendAdminInvite(Gym gym, AppUser admin) {
        send(template, "Ya tienes acceso a administrar " + gym.getName(), gym, admin);
    }

    // Misma mecánica que sendAdminInvite pero con la plantilla/copy de demo-invite.html — nunca
    // confundir los dos: este es un DEMO_ADMIN de solo-lectura, no un GYM_ADMIN real.
    public void sendDemoInvite(Gym gym, AppUser demoAdmin) {
        send(demoTemplate, "Tu demo de mygym está lista", gym, demoAdmin);
    }

    private void send(String htmlTemplate, String subject, Gym gym, AppUser admin) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("RESEND_API_KEY no configurada — se omite el email de invitación para {}", admin.getEmail());
            return;
        }
        try {
            String html = renderHtml(htmlTemplate, gym, admin);
            Map<String, Object> body = Map.of(
                    "from", from,
                    "to", java.util.List.of(admin.getEmail()),
                    "subject", subject,
                    "html", html);
            restClient
                    .post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Email de invitación enviado a {} para el gimnasio {}", admin.getEmail(), gym.getName());
        } catch (RestClientException e) {
            log.error("Falló el envío del email de invitación a {}: {}", admin.getEmail(), e.getMessage());
        }
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

    // Ver el mismo fix/comentario en MemberLifecycleEmailService — gym.logoSvg guarda tanto SVG
    // crudo como data:image/... (raster subido desde Marca) en la misma columna; sin este branch,
    // un logo raster caía como texto sin renderizar dentro del <div> y solo se veía el círculo
    // de color liso.
    //
    // El <svg> inline (rama de abajo, ahora eliminada) tampoco servía: Gmail y Outlook no
    // renderizan SVG embebido en el HTML del email de forma confiable (solo Apple Mail sí) —
    // rasterizarlo a PNG en el backend requeriría sumar una librería nueva, así que para email
    // se usa el mismo fallback de inicial que "sin logo"; el SVG real se sigue viendo en la web.
    private String logoBadgeInner(Gym gym) {
        String logoSvg = gym.getLogoSvg();
        if (logoSvg == null || logoSvg.isBlank()) {
            return escapeHtml(GymPalette.initialOf(gym.getName()));
        }
        if (logoSvg.startsWith("data:image")) {
            return "<img src=\"" + logoSvg
                    + "\" width=\"64\" height=\"64\" alt=\"\" style=\"width:64px;height:64px;border-radius:32px;display:block;object-fit:cover;\" />";
        }
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
