package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.GymBlock;
import com.cortesdev.mygym.services.PushMessage.Urgency;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Todos los textos de los avisos push, en un solo lugar. Cada metodo devuelve el aviso completo (titulo, cuerpo, tag,
 * pantalla que abre, vigencia y urgencia). Los textos van en espanol de Chile, cortos (una notificacion de telefono se
 * corta a ~2 lineas) y sin datos sensibles: nunca correo ni montos.
 */
public final class PushMessages {

    private static final ZoneId GYM_ZONE = ZoneId.of("America/Santiago");
    private static final Locale CL = new Locale("es", "CL");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d", CL);
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MIN_TTL_SECONDS = 600;
    private static final int MAX_TTL_SECONDS = 86_400;
    static final String MEMBER_URL = "/member";
    static final String STAFF_URL = "/gym-admin";

    private PushMessages() {}

    // ---------------------------------------------------------------------------------------
    // Socio
    // ---------------------------------------------------------------------------------------

    /** Primero de la lista de espera: tiene una ventana de ventaja antes de avisarle al resto. */
    public static PushMessage waitlistHeadStart(GymBlock block, LocalDate classDate, long headStartMinutes) {
        return new PushMessage(
                "¡Se liberó un cupo!",
                when(block, classDate) + ". Tienes " + headStartMinutes + " min de ventaja.",
                waitlistTag(block, classDate),
                MEMBER_URL,
                clamp(Math.toIntExact(headStartMinutes * 60)),
                Urgency.HIGH);
    }

    /** Resto de la lista: el cupo sigue libre y gana quien reserve primero. */
    public static PushMessage waitlistSpotOpen(GymBlock block, LocalDate classDate) {
        return new PushMessage(
                "Todavía hay cupo en " + block.getLabel(),
                when(block, classDate) + ". El primero que reserve se lo queda.",
                waitlistTag(block, classDate),
                MEMBER_URL,
                secondsUntilStart(block, classDate),
                Urgency.HIGH);
    }

    public static PushMessage classReminder(Gym gym, GymBlock block, LocalDate classDate, int minutesBefore, long reservationId) {
        String lead = minutesBefore >= 60 ? "1 hora" : minutesBefore + " minutos";
        return new PushMessage(
                "Tu clase empieza en " + lead,
                block.getLabel() + " a las " + HOUR.format(block.getStartTime()) + " en " + gym.getName(),
                "reminder-" + reservationId,
                MEMBER_URL,
                secondsUntilStart(block, classDate),
                Urgency.HIGH);
    }

    public static PushMessage closureCancellation(Gym gym, String reason, List<String> labels, long closureId) {
        String title = gym.getName() + (labels.size() == 1 ? " canceló tu clase" : " canceló tus clases");
        String body = capitalize(reason) + (labels.isEmpty() ? "" : ": " + String.join(", ", labels.stream().limit(2).toList())
                + (labels.size() > 2 ? " y " + (labels.size() - 2) + " más" : ""));
        return new PushMessage(title, body, "closure-" + closureId, MEMBER_URL, MAX_TTL_SECONDS, Urgency.NORMAL);
    }

    public static PushMessage membershipExpiringSoon(Gym gym, long daysRemaining) {
        String when = daysRemaining == 1 ? "mañana" : "en " + daysRemaining + " días";
        return new PushMessage(
                "Tu plan vence " + when,
                "Renueva para seguir reservando clases en " + gym.getName() + ".",
                "membership",
                MEMBER_URL,
                MAX_TTL_SECONDS,
                Urgency.NORMAL);
    }

    public static PushMessage membershipExpired(Gym gym) {
        return new PushMessage(
                "Tu membresía venció",
                "Renueva cuando quieras para seguir entrenando en " + gym.getName() + ".",
                "membership",
                MEMBER_URL,
                MAX_TTL_SECONDS,
                Urgency.NORMAL);
    }

    public static PushMessage classesExhausted(String validUntil) {
        return new PushMessage(
                "Usaste todas tus clases",
                "Tu plan sigue vigente hasta el " + validUntil + ". Si cancelas una reserva a tiempo, puedes usar esa clase en otra.",
                "classes-exhausted",
                MEMBER_URL,
                MAX_TTL_SECONDS,
                Urgency.NORMAL);
    }

    // ---------------------------------------------------------------------------------------
    // Equipo del gimnasio (admin y profesores)
    // ---------------------------------------------------------------------------------------

    /**
     * Un socio cancelo su reserva. Si ya hubo otras cancelaciones recientes de la misma clase, el aviso las cuenta
     * (comparte el tag: el telefono deja un solo aviso actualizado en vez de apilar uno por cancelacion).
     */
    public static PushMessage memberCancelled(
            GymBlock block, LocalDate classDate, String memberName, int recentCancellations, int freeSpots, int waiting) {
        String head = recentCancellations > 1 ? recentCancellations + " cancelaciones" : "Cancelación";
        String who = recentCancellations > 1 ? memberName + " y " + (recentCancellations - 1) + " más cancelaron" : memberName + " canceló";
        String spots = freeSpots == 1 ? "Queda 1 cupo" : "Quedan " + freeSpots + " cupos";
        String queue = waiting > 0 ? " · " + waiting + " en espera" : "";
        return new PushMessage(
                head + " · " + block.getLabel() + " " + HOUR.format(block.getStartTime()),
                who + " (" + DAY.format(classDate) + "). " + spots + queue + ".",
                "cancel-" + block.getId() + "-" + classDate,
                STAFF_URL,
                secondsUntilStart(block, classDate),
                Urgency.NORMAL);
    }

    // ---------------------------------------------------------------------------------------

    private static String waitlistTag(GymBlock block, LocalDate classDate) {
        return "waitlist-" + block.getId() + "-" + classDate;
    }

    private static String when(GymBlock block, LocalDate classDate) {
        return block.getLabel() + " · " + DAY.format(classDate) + " a las " + HOUR.format(block.getStartTime());
    }

    /** Segundos que faltan para que empiece la clase, acotado: el servicio de push no guarda un aviso de una clase pasada. */
    static int secondsUntilStart(GymBlock block, LocalDate classDate) {
        ZonedDateTime start = classDate.atTime(block.getStartTime()).atZone(GYM_ZONE);
        long seconds = Duration.between(ZonedDateTime.now(GYM_ZONE), start).getSeconds();
        return clamp(seconds);
    }

    private static int clamp(long seconds) {
        return (int) Math.max(MIN_TTL_SECONDS, Math.min(MAX_TTL_SECONDS, seconds));
    }

    private static String capitalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
