package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymSubscription;
import com.cortesdev.mygym.models.GymSubscriptionPayment;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.repositories.AppUserRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Avisos de la suscripcion de un gimnasio a mygym: correo + push a TODOS los administradores del gimnasio (el push solo les
 * llega a quienes tienen la app instalada y avisos activados) y correo a mygym cuando hay algo que hacer (cobrar, confirmar una
 * transferencia, emitir la factura). Todo es best-effort: un aviso que falla nunca revierte un pago ni el cron.
 */
@Component
@RequiredArgsConstructor
public class GymBillingNotifier {

    private static final Logger log = LoggerFactory.getLogger(GymBillingNotifier.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", new Locale("es", "CL"));
    private static final String PANEL_URL = "https://www.mygym.cl/gym-admin";
    private static final String SUPER_PANEL_URL = "https://www.mygym.cl/admin/gyms";

    private final AppUserRepository appUserRepository;
    private final MemberLifecycleEmailService emailService;
    private final PushNotificationService pushService;

    @Value("${app.contact.email:contacto@mygym.cl}")
    private String mygymEmail;

    // ---- Al gimnasio ---------------------------------------------------------------------------------------------------

    public void expiring(Gym gym, long daysRemaining, LocalDate due) {
        String when = daysRemaining == 1 ? "mañana" : "en " + daysRemaining + " días";
        toAdmins(
                gym,
                PushMessages.billingExpiring(daysRemaining),
                "Tu plan mygym vence " + when,
                "Renovación",
                "Tu plan mygym vence " + when,
                p("El plan de <strong style=\"color:#eaf6f7;\">" + esc(gym.getName()) + "</strong> vence el <strong style=\"color:#eaf6f7;\">"
                        + DATE.format(due) + "</strong>. Renueva desde tu panel, con Flow o por transferencia, para que tu gimnasio siga funcionando sin interrupciones."),
                "Renovar mi plan");
    }

    public void dueToday(Gym gym, LocalDate due) {
        toAdmins(
                gym,
                PushMessages.billingDueToday(),
                "Tu plan mygym vence hoy",
                "Renovación",
                "Tu plan mygym vence hoy",
                p("El plan de <strong style=\"color:#eaf6f7;\">" + esc(gym.getName()) + "</strong> vence hoy. Renuévalo desde tu panel para evitar que tu gimnasio se pause."),
                "Renovar mi plan");
        toMygym(
                gym,
                "Vence hoy el plan de " + gym.getName(),
                "Vencimiento",
                gym.getName() + " vence hoy",
                p("El plan de <strong style=\"color:#eaf6f7;\">" + esc(gym.getName()) + "</strong> vence hoy (" + DATE.format(due)
                        + "). Si no paga, entra en gracia mañana y se suspende en 3 días."));
    }

    public void grace(Gym gym, long daysUntilSuspension) {
        String when = daysUntilSuspension == 1 ? "mañana" : "en " + daysUntilSuspension + " días";
        toAdmins(
                gym,
                PushMessages.billingGrace(daysUntilSuspension),
                "Tu plan mygym está vencido",
                "Plan vencido",
                "Tu gimnasio se pausará " + when,
                p("El plan de <strong style=\"color:#eaf6f7;\">" + esc(gym.getName()) + "</strong> está vencido. Si no lo renuevas, tu gimnasio se pausará <strong style=\"color:#eaf6f7;\">"
                        + when + "</strong>: tus socios y tu equipo no podrán entrar hasta que se regularice."),
                "Renovar mi plan");
    }

    public void suspended(Gym gym) {
        toAdmins(
                gym,
                PushMessages.billingSuspended(),
                "Tu gimnasio está en pausa",
                "Gimnasio en pausa",
                "Tu gimnasio está en pausa por falta de pago",
                p("Pausamos <strong style=\"color:#eaf6f7;\">" + esc(gym.getName()) + "</strong> porque el plan mygym no se renovó. Tus datos están resguardados."
                        + " Paga con Flow desde tu panel y se reactiva al instante, o transfiere y avísanos con «Ya transferí»."),
                "Pagar y reactivar");
        toMygym(
                gym,
                gym.getName() + " quedó suspendido por no pago",
                "Suspensión",
                gym.getName() + " quedó suspendido",
                p("<strong style=\"color:#eaf6f7;\">" + esc(gym.getName()) + "</strong> se suspendió por falta de pago."));
    }

    public void paid(Gym gym, GymSubscription sub, GymSubscriptionPayment payment, LocalDate nextDue) {
        toAdmins(
                gym,
                PushMessages.billingPaid(DATE.format(nextDue)),
                "Pago recibido, gracias",
                "Pago confirmado",
                "¡Pago confirmado!",
                p("Recibimos el pago del plan mygym de <strong style=\"color:#eaf6f7;\">" + esc(gym.getName())
                        + "</strong>. Tu plan queda al día hasta el <strong style=\"color:#eaf6f7;\">" + DATE.format(nextDue) + "</strong>."),
                "Ver mi suscripción");
        // A mygym le llega todo lo necesario para emitir la factura.
        toMygym(
                gym,
                "Pago de " + gym.getName() + ": emitir factura",
                "Pago recibido",
                "Pago de " + gym.getName() + " (" + method(payment.getMethod()) + ")",
                p("Total pagado: <strong style=\"color:#eaf6f7;\">" + clp(payment.getAmountClp()) + "</strong> (neto " + clp(payment.getNetClp()) + " + IVA "
                        + clp(payment.getIvaClp()) + (payment.getFlowFeeClp() > 0 ? " + comisión de Flow " + clp(payment.getFlowFeeClp()) : "") + ").")
                        + p("Datos para la factura:<br>" + billingBlock(sub))
                        + p("Cubre hasta el " + DATE.format(nextDue) + "."));
    }

    public void transferRejected(Gym gym, String reason) {
        toAdmins(
                gym,
                PushMessages.billingTransferRejected(),
                "No pudimos confirmar tu transferencia",
                "Transferencia",
                "No pudimos confirmar tu transferencia",
                p("Revisamos el aviso de transferencia de <strong style=\"color:#eaf6f7;\">" + esc(gym.getName()) + "</strong> y no pudimos confirmarla."
                        + (reason == null || reason.isBlank() ? "" : " Motivo: " + esc(reason) + ".")
                        + " Revisa los datos y vuelve a avisarnos, o escríbenos a contacto@mygym.cl."),
                "Ir a mi suscripción");
    }

    // ---- A mygym -------------------------------------------------------------------------------------------------------

    public void transferReported(Gym gym, GymSubscriptionPayment payment) {
        toMygym(
                gym,
                gym.getName() + " avisó una transferencia",
                "Transferencia",
                gym.getName() + " dice que ya transfirió",
                p("Monto esperado: <strong style=\"color:#eaf6f7;\">" + clp(payment.getAmountClp()) + "</strong>.<br>Fecha: "
                        + (payment.getTransferDate() == null ? "-" : DATE.format(payment.getTransferDate())) + "<br>Banco: " + esc(payment.getTransferBank())
                        + "<br>Operación: " + esc(payment.getTransferOperation() == null ? "-" : payment.getTransferOperation()))
                        + p("Confírmala en la ficha del gimnasio para reactivarlo."));
    }

    // ---- Envio ---------------------------------------------------------------------------------------------------------

    private void toAdmins(Gym gym, PushMessage push, String subject, String badge, String headline, String bodyHtml, String cta) {
        try {
            List<AppUser> admins = appUserRepository.findByGymIdAndRole(gym.getId(), Role.GYM_ADMIN).stream()
                    .filter(AppUser::isActive)
                    .toList();
            for (AppUser admin : admins) {
                emailService.sendNotice(
                        gym,
                        admin.getEmail(),
                        subject,
                        badge,
                        headline,
                        bodyHtml,
                        cta,
                        PANEL_URL,
                        "Recibiste este correo porque administras " + esc(gym.getName()) + " en mygym.");
            }
            pushService.notifyUsers(admins.stream().map(AppUser::getId).toList(), push);
        } catch (RuntimeException e) {
            log.warn("No se pudo avisar la suscripción del gimnasio {}: {}", gym.getId(), e.getMessage());
        }
    }

    private void toMygym(Gym gym, String subject, String badge, String headline, String bodyHtml) {
        try {
            emailService.sendNotice(
                    gym, mygymEmail, subject, badge, headline, bodyHtml, "Abrir el panel", SUPER_PANEL_URL, "Aviso interno de mygym.");
        } catch (RuntimeException e) {
            log.warn("No se pudo avisar a mygym sobre el gimnasio {}: {}", gym.getId(), e.getMessage());
        }
    }

    private static String p(String html) {
        return "<p style=\"margin:0 0 12px;\">" + html + "</p>";
    }

    private static String billingBlock(GymSubscription sub) {
        return "RUT: " + esc(or(sub.getBillingRut())) + "<br>Razón social: " + esc(or(sub.getBillingName())) + "<br>Giro: " + esc(or(sub.getBillingGiro()))
                + "<br>Dirección: " + esc(or(sub.getBillingAddress())) + "<br>Correo: " + esc(or(sub.getBillingEmail()));
    }

    private static String or(String value) {
        return value == null || value.isBlank() ? "(sin datos)" : value;
    }

    private static String method(String method) {
        return switch (method == null ? "" : method) {
            case GymSubscriptionPayment.FLOW -> "Flow";
            case GymSubscriptionPayment.TRANSFER -> "transferencia";
            default -> "pago manual";
        };
    }

    static String clp(int amount) {
        return "$" + String.format("%,d", amount).replace(",", ".");
    }

    private static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
