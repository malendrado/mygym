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

    public AdminInviteEmailService(
            RestClient.Builder restClientBuilder,
            @Value("${app.resend.api-key}") String apiKey,
            @Value("${app.resend.from}") String from) {
        this.restClient = restClientBuilder.baseUrl("https://api.resend.com").build();
        this.apiKey = apiKey;
        this.from = from;
        this.template = loadTemplate();
    }

    public void sendAdminInvite(Gym gym, AppUser admin) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("RESEND_API_KEY no configurada — se omite el email de invitación para {}", admin.getEmail());
            return;
        }
        try {
            String html = renderHtml(gym, admin);
            Map<String, Object> body = Map.of(
                    "from", from,
                    "to", java.util.List.of(admin.getEmail()),
                    "subject", "Ya tienes acceso a administrar " + gym.getName(),
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

    private String renderHtml(Gym gym, AppUser admin) {
        String themeColor = gym.getThemeColor() != null ? gym.getThemeColor() : GymPalette.defaultHex();
        String contrast = GymPalette.contrastFor(themeColor);

        return template
                .replace("{{GYM_NAME}}", escapeHtml(gym.getName()))
                .replace("{{ADMIN_NAME}}", escapeHtml(admin.getName()))
                .replace("{{ADMIN_EMAIL}}", escapeHtml(admin.getEmail()))
                .replace("{{INVITER_NAME}}", "el equipo de mygym")
                .replace("{{INVITE_URL}}", LOGIN_URL)
                .replace("{{THEME_COLOR}}", themeColor)
                .replace("{{THEME_CONTRAST}}", contrast)
                .replace("{{LOGO_BADGE_INNER}}", logoBadgeInner(gym, contrast));
    }

    private String logoBadgeInner(Gym gym, String contrast) {
        String logoSvg = gym.getLogoSvg();
        if (logoSvg == null || logoSvg.isBlank()) {
            return escapeHtml(GymPalette.initialOf(gym.getName()));
        }
        String sized = logoSvg.replaceFirst("<svg ", "<svg width=\"34\" height=\"34\" ");
        return "<div style=\"width:34px;height:34px;color:" + contrast + ";\">" + sized + "</div>";
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

    private String loadTemplate() {
        try {
            byte[] bytes = StreamUtils.copyToByteArray(
                    new ClassPathResource("templates/email/admin-invite.html").getInputStream());
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cargar la plantilla de email admin-invite.html", e);
        }
    }
}
