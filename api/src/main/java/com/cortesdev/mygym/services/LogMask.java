package com.cortesdev.mygym.services;

/**
 * Enmascara datos personales antes de escribirlos en los logs (auditoría 2026-10-17: los correos de socios y administradores
 * quedaban completos en los logs de Railway). Deja lo justo para reconocer la cuenta al depurar: "j***@gmail.com".
 */
final class LogMask {

    private LogMask() {}

    static String email(String email) {
        if (email == null || email.isBlank()) {
            return "(sin email)";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
