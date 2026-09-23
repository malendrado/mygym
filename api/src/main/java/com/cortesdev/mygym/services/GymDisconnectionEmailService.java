package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymDeletionAudit;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Emails de la desvinculación de un gimnasio (ver GymDisconnectionService) — deliberadamente
 * separado de los otros *EmailService: acá el adjunto (CSV con el detalle completo de los
 * socios) es el punto central, no una plantilla de marca. A los admins del gym se les manda el
 * resumen + el detalle completo (para que no pierdan su cartera de clientes); al super-admin
 * (contacto@mygym.cl) solo el resumen, sin datos personales — ver la conversación con el
 * usuario del 2026-09-23 sobre por qué esta asimetría es deliberada, no un descuido.
 */
@Service
public class GymDisconnectionEmailService {

    private static final Logger log = LoggerFactory.getLogger(GymDisconnectionEmailService.class);
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Santiago"));

    private final RestClient restClient;
    private final String apiKey;
    private final String from;

    public GymDisconnectionEmailService(
            RestClient.Builder restClientBuilder,
            @Value("${app.resend.api-key}") String apiKey,
            @Value("${app.resend.from}") String from) {
        this.restClient = restClientBuilder.baseUrl("https://api.resend.com").build();
        this.apiKey = apiKey;
        this.from = from;
    }

    /** @return true si Resend confirmó el envío — el llamador (GymDisconnectionService) aborta
     *  TODA la desvinculación si esto da false, para no borrar la cartera de clientes de un gym
     *  sin que su dueño se haya quedado con una copia. */
    public boolean sendToGymAdmin(String adminEmail, Gym gym, GymDeletionAudit audit, String membersCsv, String paymentsCsv) {
        String subject = "Se desvinculó " + gym.getName() + " de mygym — datos adjuntos";
        String html = buildHtml(gym, audit, true);
        List<Map<String, String>> attachments = List.of(
                attachment("socios.csv", membersCsv), attachment("pagos.csv", paymentsCsv));
        return send(adminEmail, subject, html, attachments);
    }

    /** Best-effort: a diferencia del email al admin de arriba, si este falla no aborta la
     *  desvinculación — es solo la notificación interna de mygym, no implica pérdida de datos
     *  para nadie. */
    public void sendSummaryToSuperAdmin(String toEmail, Gym gym, GymDeletionAudit audit) {
        String subject = "Desvinculación ejecutada: " + gym.getName();
        String html = buildHtml(gym, audit, false);
        send(toEmail, subject, html, List.of());
    }

    private Map<String, String> attachment(String filename, String csvContent) {
        String base64 = Base64.getEncoder().encodeToString(csvContent.getBytes(StandardCharsets.UTF_8));
        return Map.of("filename", filename, "content", base64);
    }

    private String buildHtml(Gym gym, GymDeletionAudit audit, boolean includesAttachments) {
        String attachmentNote = includesAttachments
                ? "<p style=\"margin:16px 0 0; color:#a9c2c6;\">Adjuntamos dos archivos: "
                        + "<strong style=\"color:#eaf6f7;\">socios.csv</strong> (nombre, email, plan e historial de cada socio) y "
                        + "<strong style=\"color:#eaf6f7;\">pagos.csv</strong> (historial de pagos) — es tu única copia, "
                        + "mygym no la conserva.</p>"
                : "<p style=\"margin:16px 0 0; color:#a9c2c6;\">Este resumen no incluye datos personales de los socios — "
                        + "el detalle completo se le entregó únicamente a los administradores del gimnasio.</p>";
        return "<!DOCTYPE html><html lang=\"es\"><body style=\"margin:0; padding:0; background-color:#ffffff; "
                + "font-family:Helvetica Neue, Helvetica, Arial, sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                + "style=\"background-color:#ffffff;\"><tr><td align=\"center\" style=\"padding:32px 16px;\">"
                + "<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                + "style=\"max-width:600px; width:100%; background-color:#1c282c; border-radius:16px; padding:32px;\">"
                + "<tr><td style=\"font-size:20px; font-weight:bold; color:#eaf6f7; padding-bottom:12px;\">"
                + escapeHtml(gym.getName()) + " fue desvinculado de mygym</td></tr>"
                + "<tr><td style=\"font-size:14px; line-height:1.6; color:#a9c2c6;\">"
                + row("Slug", gym.getSlug())
                + row("Socios", String.valueOf(audit.getMemberCount()))
                + row("Reservas", String.valueOf(audit.getReservationCount()))
                + row("Pagos", String.valueOf(audit.getPaymentCount()))
                + row("Planes", String.valueOf(audit.getPlanCount()))
                + row("Bloques de horario", String.valueOf(audit.getBlockCount()))
                + row("Fotos", String.valueOf(audit.getPhotoCount()))
                + row("Ejecutado por", audit.getExecutedBy())
                + row("Fecha", DATE_FORMAT.format(audit.getExecutedAt()))
                + "</td></tr>"
                + "<tr><td>" + attachmentNote + "</td></tr>"
                + "<tr><td style=\"padding-top:20px; font-size:12px; color:#5b6b70;\">"
                + "Todos los datos de este gimnasio ya fueron eliminados permanentemente de mygym.</td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    private String row(String label, String value) {
        return "<div style=\"margin:4px 0;\"><strong style=\"color:#eaf6f7;\">" + escapeHtml(label) + ":</strong> "
                + escapeHtml(value) + "</div>";
    }

    private boolean send(String to, String subject, String html, List<Map<String, String>> attachments) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("RESEND_API_KEY no configurada — se omite el email '{}' para {}", subject, to);
            return false;
        }
        try {
            Map<String, Object> body = attachments.isEmpty()
                    ? Map.of("from", from, "to", List.of(to), "subject", subject, "html", html)
                    : Map.of(
                            "from", from,
                            "to", List.of(to),
                            "subject", subject,
                            "html", html,
                            "attachments", attachments);
            restClient
                    .post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Email de desvinculación '{}' enviado a {}", subject, to);
            return true;
        } catch (RestClientException e) {
            log.error("Falló el envío del email de desvinculación a {}: {}", to, e.getMessage());
            return false;
        }
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
}
