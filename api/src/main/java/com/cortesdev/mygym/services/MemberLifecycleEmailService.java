package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymPlan;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
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
 * Emails transaccionales del ciclo de vida de un socio: bienvenida al
 * unirse, aviso al admin de un socio nuevo, y confirmación de pago (real,
 * vía Flow.cl — ver FlowSubscriptionService.handleWebhook — o manual, ver
 * GymService.simulatePlanPayment) a socio y admin. Mismo patrón que
 * AdminInviteEmailService (Resend, best-effort: una
 * falla acá nunca rompe el flujo que dispara el envío) y misma plantilla
 * visual (templates/email/notification.html) — lo único que cambia entre
 * envíos es el contenido.
 */
@Service
public class MemberLifecycleEmailService {

    private static final Logger log = LoggerFactory.getLogger(MemberLifecycleEmailService.class);
    private static final String LOGIN_URL = "https://www.mygym.cl/login";
    private static final ZoneId GYM_ZONE = ZoneId.of("America/Santiago");
    private static final DateTimeFormatter IMPORT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final RestClient restClient;
    private final String apiKey;
    private final String from;
    private final String template;

    public MemberLifecycleEmailService(
            RestClient.Builder restClientBuilder,
            @Value("${app.resend.api-key}") String apiKey,
            @Value("${app.resend.from}") String from) {
        this.restClient = restClientBuilder.baseUrl("https://api.resend.com").build();
        this.apiKey = apiKey;
        this.from = from;
        this.template = loadTemplate();
    }

    public void sendMemberWelcome(Gym gym, AppUser member) {
        String headline = "¡Bienvenido a " + gym.getName() + ", " + firstName(member) + "!";
        String body = "<p style=\"margin:0 0 12px;\">Tu cuenta ya está lista — desde ahora puedes reservar tus clases en "
                + "<strong style=\"color:#eaf6f7;\">" + escapeHtml(gym.getName())
                + "</strong> directo desde el celular, sin escribir por WhatsApp ni esperar respuesta.</p>"
                + "<p style=\"margin:0;\">Elige un plan y agenda tu primera clase — te está esperando.</p>";
        send(
                gym,
                member.getEmail(),
                "¡Ya eres parte de " + gym.getName() + "!",
                "Nuevo socio",
                headline,
                body,
                "Entrar con Google",
                LOGIN_URL,
                "Recibiste este correo porque te uniste a " + escapeHtml(gym.getName()) + " a través de mygym.");
    }

    // Alta manual del admin ("Agregar socio") — a diferencia de sendMemberWelcome
    // (disparado desde el alta pública en /j/{slug}, cuando el socio ya se unió
    // solo), acá el socio todavía no hizo nada: el objetivo es motivarlo a
    // activar su cuenta con Google Y, de paso, tentarlo a elegir un plan de
    // una vez — por eso el cuerpo incluye un teaser con los planes activos del
    // gimnasio en vez de solo un aviso de bienvenida.
    public void sendMemberInviteWithPlans(Gym gym, AppUser member, List<GymPlan> activePlans) {
        String joinUrl = "https://www.mygym.cl/j/" + gym.getSlug();
        String name = firstName(member);
        String headline = "¡" + name + ", bienvenido a " + gym.getName() + "!";
        StringBuilder body = new StringBuilder();
        body.append("<p style=\"margin:0 0 12px;\"><strong style=\"color:#eaf6f7;\">")
                .append(escapeHtml(gym.getName()))
                .append("</strong> te sumó como socio — tu cupo ya está reservado, solo falta activarlo con tu cuenta de Google.</p>");
        if (activePlans.isEmpty()) {
            body.append("<p style=\"margin:0;\">Elige un plan apenas entres y reserva tu primera clase.</p>");
        } else {
            body.append(
                    "<p style=\"margin:0 0 16px;\">De paso, mira los planes disponibles: la mayoría de los socios elige "
                            + "el suyo apenas entra, así reserva su primera clase el mismo día.</p>");
            body.append(planTeaserHtml(gym, activePlans));
        }
        send(
                gym,
                member.getEmail(),
                name + ", tu cupo en " + gym.getName() + " ya está reservado",
                "Cupo reservado",
                headline,
                body.toString(),
                activePlans.isEmpty() ? "Activar mi cupo" : "Activar mi cupo y ver planes",
                joinUrl,
                "Recibiste este correo porque " + escapeHtml(gym.getName()) + " te agregó como socio en mygym.");
    }

    // Máximo 2 planes en la tarjeta (más se ve saturado en 600px de ancho de
    // email) — sin un campo "recomendado" en GymPlan, el de mayor precio se
    // marca como "Más elegido" (proxy razonable: normalmente el plan full/
    // ilimitado cuesta más), solo cuando hay más de uno para comparar.
    private String planTeaserHtml(Gym gym, List<GymPlan> activePlans) {
        String themeColor = gym.getThemeColor() != null ? gym.getThemeColor() : GymPalette.defaultHex();
        String themeContrast = GymPalette.contrastFor(themeColor);
        List<GymPlan> featured = activePlans.stream()
                .sorted(Comparator.comparing(GymPlan::getPriceClp, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(2)
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append(
                "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin:0 0 20px;\"><tr>");
        for (int i = 0; i < featured.size(); i++) {
            GymPlan plan = featured.get(i);
            boolean isFeatured = i == 0 && featured.size() > 1;
            String quota = plan.getMonthlyClasses() == null
                    ? "Clases ilimitadas"
                    : plan.getMonthlyClasses() + " clases al mes";
            String borderColor = isFeatured ? themeColor : "#2f3d41";
            sb.append("<td width=\"").append(100 / featured.size()).append("%\" valign=\"top\" style=\"padding:0 6px;\">");
            sb.append("<div style=\"background-color:#12191c; border:1px solid ")
                    .append(borderColor)
                    .append("; border-radius:14px; padding:16px;\">");
            if (isFeatured) {
                sb.append("<div style=\"display:inline-block; background-color:")
                        .append(themeColor)
                        .append("; color:")
                        .append(themeContrast)
                        .append("; font-size:10px; font-weight:bold; letter-spacing:0.06em; text-transform:uppercase; "
                                + "padding:3px 9px; border-radius:999px; margin-bottom:8px;\">Más elegido</div><br/>");
            }
            sb.append("<div style=\"font-size:13px; font-weight:bold; color:#eaf6f7; margin-bottom:6px;\">")
                    .append(escapeHtml(plan.getName()))
                    .append("</div>");
            sb.append("<div style=\"font-family:'Courier New', Courier, monospace; font-size:20px; font-weight:bold; color:#eaf6f7;\">$")
                    .append(formatClp(plan.getPriceClp()))
                    .append("<span style=\"font-family:Helvetica Neue, Helvetica, Arial, sans-serif; font-size:12px; "
                            + "font-weight:normal; color:#7d9296;\">/mes</span></div>");
            sb.append("<div style=\"margin-top:6px; font-size:12px; color:#a9c2c6;\">")
                    .append(quota)
                    .append("</div>");
            sb.append("</div></td>");
        }
        sb.append("</tr></table>");
        return sb.toString();
    }

    public void sendNewMemberNotice(Gym gym, AppUser member, List<String> adminEmails) {
        String headline = "Tienes un socio nuevo en " + gym.getName();
        String body = "<p style=\"margin:0 0 12px;\"><strong style=\"color:#eaf6f7;\">" + escapeHtml(member.getName())
                + "</strong> (" + escapeHtml(member.getEmail())
                + ") se acaba de unir a tu gimnasio. Un socio más, un problema menos por resolver a mano.</p>"
                + "<p style=\"margin:0;\">Revisa su ficha desde tu panel cuando quieras.</p>";
        for (String adminEmail : adminEmails) {
            send(
                    gym,
                    adminEmail,
                    "Nuevo socio en " + gym.getName(),
                    "Aviso de socios",
                    headline,
                    body,
                    "Ver mi panel",
                    LOGIN_URL,
                    "Recibiste este correo porque administras " + escapeHtml(gym.getName()) + " en mygym.");
        }
    }

    // Contraparte de sendPaymentConfirmedMember para socios cargados por Excel (ver
    // MemberImportRowService) — a diferencia del pago real vía Flow/simulatePlanPayment, acá el
    // socio NUNCA se logueó todavía (mismo estado que sendMemberInviteWithPlans), así que hace
    // falta: 1) saludarlo por nombre (el genérico "Pago confirmado" del otro método no lo hace
    // porque ahí el socio ya sabe quién es — recién pagó), 2) decir la fecha de vencimiento que
    // el admin cargó a mano (el otro método nunca la dice porque paidAt siempre es "ahora", el
    // vencimiento siempre está a un mes en el futuro), y 3) avisar explícitamente si ese
    // vencimiento YA PASÓ — reportado por el usuario tras recibir un "¡Pago confirmado! Ya
    // tienes tu plan activo" para un socio que, según la fecha que él mismo cargó, ya estaba
    // vencido.
    public void sendMemberImportedWithPlan(
            Gym gym, AppUser member, GymPlan plan, ZonedDateTime periodEnd, Integer usedSessions) {
        String name = firstName(member);
        String dateLabel = IMPORT_DATE_FORMAT.format(periodEnd);
        boolean expired = periodEnd.isBefore(ZonedDateTime.now(GYM_ZONE));
        String quota = plan.getMonthlyClasses() == null
                ? "clases ilimitadas dentro de los cupos disponibles"
                : plan.getMonthlyClasses() + " clases al mes";
        // Solo tiene sentido para un plan con cupo limitado — un plan libre no tiene "usadas de cuántas".
        String usedNote = (!expired && usedSessions != null && plan.getMonthlyClasses() != null)
                ? " Ya llevas <strong style=\"color:#eaf6f7;\">" + usedSessions + " de " + plan.getMonthlyClasses()
                        + "</strong> clases usadas este mes."
                : "";
        String headline = expired ? "Tu plan " + plan.getName() + " está vencido" : "¡" + name + ", ya tienes " + plan.getName() + "!";
        String body = expired
                ? "<p style=\"margin:0 0 12px;\">" + escapeHtml(gym.getName()) + " te cargó el plan <strong style=\"color:#eaf6f7;\">"
                        + escapeHtml(plan.getName()) + "</strong>, vigente hasta el " + dateLabel
                        + " — esa fecha ya pasó, así que para reservar clases vas a necesitar renovarlo.</p>"
                        + "<p style=\"margin:0;\">Activa tu cuenta con Google para ver el detalle y renovar cuando quieras.</p>"
                : "<p style=\"margin:0 0 12px;\">" + escapeHtml(gym.getName()) + " activó tu plan <strong style=\"color:#eaf6f7;\">"
                        + escapeHtml(plan.getName()) + "</strong>, con " + quota + ", vigente hasta el " + dateLabel + "."
                        + usedNote + "</p>"
                        + "<p style=\"margin:0;\">Activa tu cuenta con Google para reservar tu primera clase.</p>";
        send(
                gym,
                member.getEmail(),
                (expired ? "Tu plan " : "Pago confirmado — ") + plan.getName() + (expired ? " está vencido" : ""),
                expired ? "Plan vencido" : "Pago confirmado",
                headline,
                body,
                "Activar mi cuenta",
                LOGIN_URL,
                "Recibiste este correo porque " + escapeHtml(gym.getName()) + " te cargó el plan "
                        + escapeHtml(plan.getName()) + " en mygym.");
    }

    public void sendPaymentConfirmedMember(Gym gym, AppUser member, GymPlan plan) {
        String quota = plan.getMonthlyClasses() == null
                ? "clases ilimitadas dentro de los cupos disponibles"
                : plan.getMonthlyClasses() + " clases al mes";
        String headline = "¡Pago confirmado! Ya tienes " + plan.getName();
        String body = "<p style=\"margin:0 0 12px;\">Tu plan <strong style=\"color:#eaf6f7;\">" + escapeHtml(plan.getName())
                + "</strong> ya está activo, con " + quota
                + ". Nada de excusas ahora — el próximo paso es reservar tu clase.</p>"
                + "<p style=\"margin:0;\">Nos vemos en " + escapeHtml(gym.getName()) + ".</p>";
        send(
                gym,
                member.getEmail(),
                "Pago confirmado — " + plan.getName(),
                "Pago confirmado",
                headline,
                body,
                "Reservar mi clase",
                LOGIN_URL,
                "Recibiste este correo porque activaste el plan " + escapeHtml(plan.getName()) + " en "
                        + escapeHtml(gym.getName()) + ".");
    }

    public void sendPaymentConfirmedAdmin(Gym gym, AppUser member, GymPlan plan, List<String> adminEmails) {
        String headline = "Nuevo pago confirmado en " + gym.getName();
        String body = "<p style=\"margin:0 0 12px;\"><strong style=\"color:#eaf6f7;\">" + escapeHtml(member.getName())
                + "</strong> activó el plan <strong style=\"color:#eaf6f7;\">" + escapeHtml(plan.getName()) + "</strong> ($"
                + formatClp(plan.getPriceClp())
                + " / mes). Un ingreso más para el gimnasio, sin que muevas un dedo.</p>"
                + "<p style=\"margin:0;\">Puedes ver el detalle desde tu panel.</p>";
        for (String adminEmail : adminEmails) {
            send(
                    gym,
                    adminEmail,
                    "Nuevo pago en " + gym.getName(),
                    "Pago confirmado",
                    headline,
                    body,
                    "Ver mi panel",
                    LOGIN_URL,
                    "Recibiste este correo porque administras " + escapeHtml(gym.getName()) + " en mygym.");
        }
    }

    // Aviso ANTES de que venza (a diferencia de sendMembershipExpiredMember,
    // que avisa cuando ya venció) — disparado por MembershipReminderJob, un
    // cron diario, no por el cliente. Con pago manual mes a mes (sin cobro
    // automático) este aviso es lo único que le recuerda al socio pagar de
    // nuevo antes de quedarse sin poder reservar.
    public void sendMembershipExpiringSoonMember(Gym gym, AppUser member, GymPlan plan, long daysRemaining) {
        String dayWord = daysRemaining == 1 ? "día" : "días";
        String headline = "Tu membresía vence en " + daysRemaining + " " + dayWord;
        String body = "<p style=\"margin:0 0 12px;\">Tu plan <strong style=\"color:#eaf6f7;\">" + escapeHtml(plan.getName())
                + "</strong> vence en " + daysRemaining + " " + dayWord
                + " — paga antes de esa fecha para no quedarte sin poder reservar.</p>"
                + "<p style=\"margin:0;\">Puedes pagar cuando quieras desde tu cuenta en " + escapeHtml(gym.getName())
                + ".</p>";
        send(
                gym,
                member.getEmail(),
                "Tu membresía en " + gym.getName() + " vence en " + daysRemaining + " " + dayWord,
                "Por vencer",
                headline,
                body,
                "Pagar mi plan",
                LOGIN_URL,
                "Recibiste este correo porque tu plan " + escapeHtml(plan.getName()) + " en " + escapeHtml(gym.getName())
                        + " está por vencer.");
    }

    public void sendMembershipExpiredMember(Gym gym, AppUser member, GymPlan plan) {
        String headline = "Tu membresía venció";
        String body = "<p style=\"margin:0 0 12px;\">Tu plan <strong style=\"color:#eaf6f7;\">" + escapeHtml(plan.getName())
                + "</strong> se cumplió un mes después de tu último pago — mientras no renueves, no vas a poder "
                + "reservar clases nuevas.</p>"
                + "<p style=\"margin:0;\">Renueva cuando quieras para seguir entrenando en " + escapeHtml(gym.getName())
                + ".</p>";
        send(
                gym,
                member.getEmail(),
                "Tu membresía en " + gym.getName() + " venció",
                "Membresía vencida",
                headline,
                body,
                "Renovar plan",
                LOGIN_URL,
                "Recibiste este correo porque tu plan " + escapeHtml(plan.getName()) + " en " + escapeHtml(gym.getName())
                        + " venció.");
    }

    public void sendMembershipExpiredAdmin(Gym gym, AppUser member, GymPlan plan, List<String> adminEmails) {
        String headline = "Un socio de " + gym.getName() + " no renovó su plan";
        String body = "<p style=\"margin:0 0 12px;\"><strong style=\"color:#eaf6f7;\">" + escapeHtml(member.getName())
                + "</strong> (" + escapeHtml(member.getEmail()) + ") tenía el plan <strong style=\"color:#eaf6f7;\">"
                + escapeHtml(plan.getName())
                + "</strong>, que venció hoy sin renovación. No puede reservar clases nuevas mientras no pague de nuevo.</p>"
                + "<p style=\"margin:0;\">Si quieres contactarlo, tienes su ficha en tu panel.</p>";
        for (String adminEmail : adminEmails) {
            send(
                    gym,
                    adminEmail,
                    "Membresía vencida en " + gym.getName(),
                    "Membresía vencida",
                    headline,
                    body,
                    "Ver mi panel",
                    LOGIN_URL,
                    "Recibiste este correo porque administras " + escapeHtml(gym.getName()) + " en mygym.");
        }
    }

    private void send(
            Gym gym,
            String to,
            String subject,
            String badgeLabel,
            String headline,
            String bodyHtml,
            String ctaText,
            String ctaUrl,
            String footerText) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("RESEND_API_KEY no configurada — se omite el email '{}' para {}", subject, to);
            return;
        }
        try {
            String html = renderHtml(gym, badgeLabel, headline, bodyHtml, ctaText, ctaUrl, footerText);
            Map<String, Object> requestBody = Map.of(
                    "from", from,
                    "to", List.of(to),
                    "subject", subject,
                    "html", html);
            restClient
                    .post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Email '{}' enviado a {}", subject, to);
        } catch (RestClientException e) {
            log.error("Falló el envío del email '{}' a {}: {}", subject, to, e.getMessage());
        }
    }

    private String renderHtml(
            Gym gym, String badgeLabel, String headline, String bodyHtml, String ctaText, String ctaUrl, String footerText) {
        String themeColor = gym.getThemeColor() != null ? gym.getThemeColor() : GymPalette.defaultHex();
        String contrast = GymPalette.contrastFor(themeColor);
        String preheader = headline.length() > 90 ? headline.substring(0, 90) : headline;

        return template
                .replace("{{PREHEADER}}", escapeHtml(preheader))
                .replace("{{GYM_NAME}}", escapeHtml(gym.getName()))
                .replace("{{THEME_COLOR}}", themeColor)
                .replace("{{THEME_CONTRAST}}", contrast)
                .replace("{{LOGO_BADGE_INNER}}", logoBadgeInner(gym))
                .replace("{{BADGE_LABEL}}", escapeHtml(badgeLabel))
                .replace("{{HEADLINE}}", escapeHtml(headline))
                .replace("{{BODY_HTML}}", bodyHtml)
                .replace("{{CTA_TEXT}}", escapeHtml(ctaText))
                .replace("{{CTA_URL}}", ctaUrl)
                .replace("{{FOOTER_TEXT}}", footerText);
    }

    // Se probaron dos formatos de gym.logoSvg como <img>/<svg> inline en el email — ninguno
    // funciona en Gmail: el <svg> crudo (ej. Fortis) no lo renderiza casi ningún cliente (solo
    // Apple Mail), y un <img src="data:..."> tampoco: Gmail directamente no muestra imágenes
    // data: (base64) embebidas, sea cual sea el formato (confirmado con un envío real, no solo
    // en teoría — un gym con logo PNG subido por archivo seguía mostrando el círculo vacío).
    // Mostrar el logo real en el email requeriría mandarlo como adjunto embebido (content-id) en
    // vez de data URI — se dejó afuera a propósito (decisión explícita del usuario) por el
    // trabajo/riesgo extra. Se usa siempre el mismo fallback de inicial acá — el logo real se
    // sigue viendo en la web/app, donde sí se renderiza bien.
    private String logoBadgeInner(Gym gym) {
        return escapeHtml(GymPalette.initialOf(gym.getName()));
    }

    private String firstName(AppUser user) {
        String name = user.getName();
        if (name == null || name.isBlank()) {
            return "socio";
        }
        return name.split(" ")[0];
    }

    private String formatClp(Integer amount) {
        if (amount == null) {
            return "0";
        }
        return String.format("%,d", amount).replace(",", ".");
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
                    new ClassPathResource("templates/email/notification.html").getInputStream());
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cargar la plantilla de email notification.html", e);
        }
    }
}
