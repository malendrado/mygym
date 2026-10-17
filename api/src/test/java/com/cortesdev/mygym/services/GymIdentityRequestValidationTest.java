package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.cortesdev.mygym.models.dto.GymIdentityUpdateRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

/** El enlace de Instagram se muestra a los socios: el servidor solo acepta http(s), no el front (auditoría 2026-10-17). */
class GymIdentityRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean valid(String instagramUrl) {
        return validator.validate(new GymIdentityUpdateRequest("lema", "desc", instagramUrl, "+56912345678")).isEmpty();
    }

    @Test
    void httpAndHttpsLinksAndNoLinkAreAccepted() {
        assertThat(valid("https://instagram.com/xalogym")).isTrue();
        assertThat(valid("http://instagram.com/xalogym")).isTrue();
        assertThat(valid(null)).isTrue();
        // Los mismos formatos que acepta la página de alta (join.ts): sin esquema y solo el usuario.
        assertThat(valid("instagram.com/xalogym")).isTrue();
        assertThat(valid("www.instagram.com/xalogym")).isTrue();
        assertThat(valid("@xalogym")).isTrue();
        assertThat(valid("xalogym")).isTrue();
    }

    @Test
    void otherSchemesAndFreeTextAreRejected() {
        assertThat(valid("javascript:alert(1)")).isFalse();
        assertThat(valid("data:text/html;base64,PHNjcmlwdD4=")).isFalse();
        assertThat(valid("//instagram.com/x")).isFalse();
        assertThat(valid("https://instagram.com/x	y")).isFalse(); // un tab no es parte de un enlace
        assertThat(valid(" https://instagram.com/x")).isFalse(); // el front hace trim antes de enviar
        assertThat(valid("https://instagram.com/x y")).isFalse();
        assertThat(valid("https://instagram.com/<script>")).isFalse();
    }
}
