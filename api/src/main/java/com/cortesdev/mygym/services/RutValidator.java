package com.cortesdev.mygym.services;

/**
 * RUT chileno — el algoritmo de dígito verificador (módulo 11) es el mismo para persona
 * natural y persona jurídica (empresa), no hay dos validaciones distintas. Réplica exacta
 * del validador del frontend (app/src/app/core/utils/rut.ts) — mismo criterio en ambas capas.
 */
public final class RutValidator {

    private RutValidator() {}

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("[^0-9kK]", "").toUpperCase();
    }

    private static char computeDv(String body) {
        int sum = 0;
        int multiplier = 2;
        for (int i = body.length() - 1; i >= 0; i--) {
            sum += (body.charAt(i) - '0') * multiplier;
            multiplier = multiplier == 7 ? 2 : multiplier + 1;
        }
        int remainder = 11 - (sum % 11);
        if (remainder == 11) return '0';
        if (remainder == 10) return 'K';
        return Character.forDigit(remainder, 10);
    }

    public static boolean isValid(String value) {
        String clean = clean(value);
        if (!clean.matches("\\d{1,8}[0-9K]")) {
            return false;
        }
        String body = clean.substring(0, clean.length() - 1);
        char dv = clean.charAt(clean.length() - 1);
        return computeDv(body) == dv;
    }

    /** Asume que {@code value} ya pasó {@link #isValid(String)}. */
    public static String format(String value) {
        String clean = clean(value);
        String body = clean.substring(0, clean.length() - 1);
        char dv = clean.charAt(clean.length() - 1);
        StringBuilder withDots = new StringBuilder();
        int count = 0;
        for (int i = body.length() - 1; i >= 0; i--) {
            withDots.insert(0, body.charAt(i));
            count++;
            if (count % 3 == 0 && i > 0) {
                withDots.insert(0, '.');
            }
        }
        return withDots + "-" + dv;
    }
}
