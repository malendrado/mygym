package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cortesdev.mygym.services.exception.InvalidLogoException;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** El logo llega a /tv y a la página pública: solo SVG sin contenido activo o PNG/JPG/WEBP con firma válida (auditoría 2026-10-18). */
class GymLogoValidationTest {

    private static final String OK_SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\"><circle r=\"4\"/></svg>";

    private static String pngUri() {
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 0, 0, 0, 0};
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
    }

    @Test
    void acceptsCleanSvgAndRealPng() {
        assertThat(GymService.validatedLogo("  " + OK_SVG + " ")).isEqualTo(OK_SVG);
        assertThat(GymService.validatedLogo(pngUri())).isEqualTo(pngUri());
    }

    @Test
    void rejectsActiveContentAndDangerousDataUris() {
        for (String bad : new String[] {
            "<svg><script>alert(1)</script></svg>",
            "<svg onload=\"alert(1)\"></svg>",
            "<svg><a href=\"jav&#x61;script:alert(1)\"><circle r=\"1\"/></a></svg>",
            "<svg><image href=\"data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=\"/></svg>",
            "data:image/svg+xml;base64,PHN2ZyBvbmxvYWQ9YWxlcnQoMSk+PC9zdmc+",
            "data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==",
            "data:image/png;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==",
            "javascript:alert(1)",
            "https://evil.example/logo.png"}) {
            assertThatThrownBy(() -> GymService.validatedLogo(bad)).as(bad).isInstanceOf(InvalidLogoException.class);
        }
    }
}
