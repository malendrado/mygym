package com.cortesdev.mygym.services;

import java.util.List;
import java.util.Locale;

/**
 * Denylist-based validation for SVG markup coming from an untrusted source
 * (AI-generated logos, or a gym admin pasting/uploading their own SVG file).
 * Shared by BrandingSuggestionService and GymService (logo upload) so the
 * rules never drift between the two entry points.
 */
public final class SvgSanitizer {

    private static final List<String> FORBIDDEN_PATTERNS = List.of(
            "<script", "javascript:", "onload=", "onerror=", "onclick=", "<iframe", "<object", "<embed",
            "<foreignobject");

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
        for (String forbidden : FORBIDDEN_PATTERNS) {
            if (lower.contains(forbidden)) {
                return "El logo contiene contenido no permitido.";
            }
        }
        return null;
    }
}
