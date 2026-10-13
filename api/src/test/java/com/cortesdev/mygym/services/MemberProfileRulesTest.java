package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cortesdev.mygym.services.exception.InvalidMemberProfileException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Unidades puras: validación y normalización de los datos de contacto del socio. */
class MemberProfileRulesTest {

    @Test
    void rutIsValidatedByCheckDigitAndStoredFormatted() {
        assertThat(MemberProfileRules.normalizeRut("123456785")).isEqualTo("12.345.678-5");
        assertThat(MemberProfileRules.normalizeRut("12.345.678-5")).isEqualTo("12.345.678-5");
        assertThatThrownBy(() -> MemberProfileRules.normalizeRut("12.345.678-9"))
                .isInstanceOf(InvalidMemberProfileException.class);
        assertThatThrownBy(() -> MemberProfileRules.normalizeRut("")).isInstanceOf(InvalidMemberProfileException.class);
        assertThatThrownBy(() -> MemberProfileRules.normalizeRut(null)).isInstanceOf(InvalidMemberProfileException.class);
    }

    @Test
    void chileanMobileIsNormalizedToInternationalFormat() {
        assertThat(MemberProfileRules.normalizePhone("9 1234 5678")).isEqualTo("+56912345678");
        assertThat(MemberProfileRules.normalizePhone("+56 9 1234 5678")).isEqualTo("+56912345678");
        assertThat(MemberProfileRules.normalizePhone("56912345678")).isEqualTo("+56912345678");
        assertThat(MemberProfileRules.normalizePhone("(9) 1234-5678")).isEqualTo("+56912345678");
    }

    @Test
    void landlinesAndMalformedNumbersAreRejected() {
        for (String bad : new String[] {"221234567", "+56 2 2123 4567", "12345", "", "+1 415 555 2671", "912345678912"}) {
            assertThatThrownBy(() -> MemberProfileRules.normalizePhone(bad))
                    .as(bad)
                    .isInstanceOf(InvalidMemberProfileException.class);
        }
    }

    @Test
    void birthDateMustBeInThePastWithAPlausibleAge() {
        LocalDate today = LocalDate.now();
        assertThat(MemberProfileRules.validateBirthDate(today.minusYears(30))).isEqualTo(today.minusYears(30));
        assertThatThrownBy(() -> MemberProfileRules.validateBirthDate(today.plusDays(1)))
                .isInstanceOf(InvalidMemberProfileException.class);
        assertThatThrownBy(() -> MemberProfileRules.validateBirthDate(today.minusYears(5)))
                .isInstanceOf(InvalidMemberProfileException.class);
        assertThatThrownBy(() -> MemberProfileRules.validateBirthDate(today.minusYears(120)))
                .isInstanceOf(InvalidMemberProfileException.class);
        assertThatThrownBy(() -> MemberProfileRules.validateBirthDate(null))
                .isInstanceOf(InvalidMemberProfileException.class);
    }

    @Test
    void addressIsTrimmedAndLengthChecked() {
        assertThat(MemberProfileRules.normalizeAddress("  Av. Siempre   Viva 742 ")).isEqualTo("Av. Siempre Viva 742");
        assertThatThrownBy(() -> MemberProfileRules.normalizeAddress("abc")).isInstanceOf(InvalidMemberProfileException.class);
        assertThatThrownBy(() -> MemberProfileRules.normalizeAddress("x".repeat(201)))
                .isInstanceOf(InvalidMemberProfileException.class);
    }

    @Test
    void emergencyContactNameIsTrimmedAndLengthChecked() {
        assertThat(MemberProfileRules.normalizeEmergencyName("  Marta   Pérez ")).isEqualTo("Marta Pérez");
        assertThatThrownBy(() -> MemberProfileRules.normalizeEmergencyName(" a ")).isInstanceOf(InvalidMemberProfileException.class);
        assertThatThrownBy(() -> MemberProfileRules.normalizeEmergencyName(null)).isInstanceOf(InvalidMemberProfileException.class);
        assertThatThrownBy(() -> MemberProfileRules.normalizeEmergencyName("x".repeat(101)))
                .isInstanceOf(InvalidMemberProfileException.class);
    }

    @Test
    void emergencyContactPhoneIsAChileanMobileWithItsOwnMessage() {
        assertThat(MemberProfileRules.normalizeEmergencyPhone("9 8765 4321")).isEqualTo("+56987654321");
        assertThatThrownBy(() -> MemberProfileRules.normalizeEmergencyPhone("22 123 4567"))
                .isInstanceOf(InvalidMemberProfileException.class)
                .hasMessageContaining("contacto de emergencia");
    }
}
