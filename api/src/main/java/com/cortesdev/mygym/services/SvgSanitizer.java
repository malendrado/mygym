package com.cortesdev.mygym.services;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validation for SVG markup coming from an untrusted source (AI-generated logos, or a gym admin
 * pasting/uploading their own SVG file). Shared by BrandingSuggestionService and GymService (logo
 * upload) so the rules never drift between the two entry points.
 *
 * <p>The frontend renders this markup with {@code [innerHTML]} + {@code bypassSecurityTrustHtml}
 * (needed so the logo keeps working as live, theme-aware SVG) — Angular's own sanitizer is fully
 * bypassed, so this is the ONLY line of defense against stored XSS. A plain substring denylist
 * (the previous version of this class) misses SMIL event handlers (`<animate onbegin="...">`,
 * which fire without any user interaction) and any `on*` attribute other than the 3-4 hardcoded
 * ones. This version forbids every `on<word>=` attribute via regex and every known
 * script-capable element, not just the handful seen in a PoC.
 */
public final class SvgSanitizer {

    private static final List<String> FORBIDDEN_SUBSTRINGS = List.of(
            "<script", "<iframe", "<object", "<embed", "<foreignobject", "<style",
            // SMIL — animar atributos y disparar un handler sin interacción del usuario.
            "<animate", "<set", "<animatetransform", "<animatemotion", "<animatecolor");

    // Cualquier atributo de evento (onload=, onclick=, onbegin=, onmouseover=, etc.) — una
    // denylist de nombres puntuales siempre deja afuera el próximo que no se nos ocurrió.
    private static final Pattern EVENT_HANDLER_ATTR = Pattern.compile("\\bon[a-z]+\\s*=", Pattern.CASE_INSENSITIVE);

    // "javascript:" con espacios/tabs/saltos de línea insertados entre letras (bypass clásico de
    // denylists por substring) — se normaliza sacando todo whitespace antes de buscar el esquema.
    private static final Pattern JAVASCRIPT_SCHEME =
            Pattern.compile("javascript\\s*:", Pattern.CASE_INSENSITIVE);

    private static final int MAX_LENGTH = 20000;

    private SvgSanitizer() {}

    public static boolean looksLikeSvg(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.strip().toLowerCase(Locale.ROOT);
        return lower.startsWith("<svg");
    }

    /** Returns null if the SVG is safe, or a Spanish error message describing why it isn't. */
    public static String validate(String svg) {
        if (svg == null || svg.isBlank()) {
            return "El logo no puede estar vacío.";
        }
        String normalized = svg.strip();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("<svg") || !lower.contains("</svg>")) {
            return "El logo no es un SVG válido.";
        }
        if (normalized.length() > MAX_LENGTH) {
            return "El logo es demasiado grande.";
        }
        for (String forbidden : FORBIDDEN_SUBSTRINGS) {
            if (lower.contains(forbidden)) {
                return "El logo contiene contenido no permitido.";
            }
        }
        // Sin espacios/control chars: "java\tscript:" colapsa a "javascript:" y cae igual.
        String collapsedWhitespace = normalized.replaceAll("[\\s\\u0000-\\u001f]", "");
        if (EVENT_HANDLER_ATTR.matcher(normalized).find()
                || JAVASCRIPT_SCHEME.matcher(normalized).find()
                || JAVASCRIPT_SCHEME.matcher(collapsedWhitespace).find()
                || lower.contains("data:text/html")
                || lower.contains("data:application")) {
            return "El logo contiene contenido no permitido.";
        }
        return null;
    }
}
