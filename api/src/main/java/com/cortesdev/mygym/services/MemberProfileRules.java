package com.cortesdev.mygym.services;

import com.cortesdev.mygym.services.exception.InvalidMemberProfileException;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;

/**
 * Reglas de los datos de contacto del socio (RUT, dirección, fecha de nacimiento, celular y contacto de emergencia) en un
 * solo lugar: las usan el propio socio, el admin y el Excel de importación. Réplica del frontend
 * (app/src/app/core/utils/member-profile.ts) — mismo criterio en ambas capas, pero el servidor
 * nunca confía en lo que validó el navegador.
 */
public final class MemberProfileRules {

    private static final ZoneId GYM_ZONE = ZoneId.of("America/Santiago");
    static final int MIN_AGE_YEARS = 12;
    static final int MAX_AGE_YEARS = 100;
    static final int MIN_ADDRESS_LENGTH = 5;
    static final int MAX_ADDRESS_LENGTH = 200;
    static final int MIN_EMERGENCY_NAME_LENGTH = 2;
    static final int MAX_EMERGENCY_NAME_LENGTH = 100;

    private MemberProfileRules() {}

    /** RUT chileno válido (dígito verificador) → "12.345.678-5". */
    public static String normalizeRut(String value) {
        if (value == null || !RutValidator.isValid(value)) {
            throw new InvalidMemberProfileException("RUT inválido. Revisa el número y el dígito verificador.");
        }
        return RutValidator.format(value);
    }

    public static String normalizeAddress(String value) {
        String address = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (address.length() < MIN_ADDRESS_LENGTH) {
            throw new InvalidMemberProfileException("Escribe tu dirección completa (calle, número y comuna).");
        }
        if (address.length() > MAX_ADDRESS_LENGTH) {
            throw new InvalidMemberProfileException("La dirección es demasiado larga (máximo " + MAX_ADDRESS_LENGTH + " letras).");
        }
        return address;
    }

    /** Celular chileno (se usa para contactar por WhatsApp): acepta "9 1234 5678", "+56 9 1234 5678",
     *  "56912345678", con espacios o guiones → "+56912345678". */
    public static String normalizePhone(String value) {
        String digits = value == null ? "" : value.replaceAll("\\D", "");
        if (digits.length() == 11 && digits.startsWith("569")) {
            digits = digits.substring(2);
        }
        if (digits.length() != 9 || digits.charAt(0) != '9') {
            throw new InvalidMemberProfileException("Ingresa un celular chileno válido, por ejemplo +56 9 1234 5678.");
        }
        return "+56" + digits;
    }

    /** Nombre del contacto de emergencia: 2 a 100 letras, espacios colapsados. */
    public static String normalizeEmergencyName(String value) {
        String name = value == null ? "" : value.trim().replaceAll("\s+", " ");
        if (name.length() < MIN_EMERGENCY_NAME_LENGTH) {
            throw new InvalidMemberProfileException("Escribe el nombre de tu contacto de emergencia.");
        }
        if (name.length() > MAX_EMERGENCY_NAME_LENGTH) {
            throw new InvalidMemberProfileException(
                    "El nombre del contacto de emergencia es demasiado largo (máximo " + MAX_EMERGENCY_NAME_LENGTH + " letras).");
        }
        return name;
    }

    /** Celular del contacto de emergencia: mismo formato que el del socio ("+56912345678"). */
    public static String normalizeEmergencyPhone(String value) {
        try {
            return normalizePhone(value);
        } catch (InvalidMemberProfileException e) {
            throw new InvalidMemberProfileException(
                    "Ingresa un celular chileno válido para tu contacto de emergencia, por ejemplo +56 9 1234 5678.");
        }
    }

    public static LocalDate validateBirthDate(LocalDate value) {
        LocalDate today = LocalDate.now(GYM_ZONE);
        if (value == null || value.isAfter(today)) {
            throw new InvalidMemberProfileException("La fecha de nacimiento no puede ser futura.");
        }
        int age = Period.between(value, today).getYears();
        if (age < MIN_AGE_YEARS || age > MAX_AGE_YEARS) {
            throw new InvalidMemberProfileException("Revisa la fecha de nacimiento: la edad debe estar entre "
                    + MIN_AGE_YEARS + " y " + MAX_AGE_YEARS + " años.");
        }
        return value;
    }
}
