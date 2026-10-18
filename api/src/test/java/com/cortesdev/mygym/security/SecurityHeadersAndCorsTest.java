package com.cortesdev.mygym.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cortesdev.mygym.controllers.GymAdminController;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.services.DemoAccessService;
import com.cortesdev.mygym.services.GymCapacityService;
import com.cortesdev.mygym.services.GymClosureService;
import com.cortesdev.mygym.services.GymService;
import com.cortesdev.mygym.services.ReservationService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Auditoría 2026-10-18: HSTS en las respuestas de la API (el TLS se termina en el proxy, la API ve HTTP) y CORS sin
 * localhost en producción. Usa la configuración de seguridad real; una ruta pública sin controlador responde 404 pero
 * con los headers de seguridad ya puestos.
 */
@WebMvcTest(GymAdminController.class)
@Import({SecurityConfig.class, JwtService.class, DemoAccessExpiryFilter.class, UserSessionFilter.class,
        GymPauseFilter.class, AuthRateLimitFilter.class})
@TestPropertySource(properties = {
    "app.jwt.secret=una-clave-de-prueba-de-al-menos-32-caracteres!!",
    "app.cors.allowed-origins=https://www.mygym.cl,https://mygym.cl"
})
class SecurityHeadersAndCorsTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private GymService gymService;
    @MockitoBean private ReservationService reservationService;
    @MockitoBean private GymClosureService gymClosureService;
    @MockitoBean private GymCapacityService gymCapacityService;
    @MockitoBean private UserSessionGuard userSessionGuard;
    @MockitoBean private GymPauseGuard gymPauseGuard;
    @MockitoBean private AppUserRepository appUserRepository;
    @MockitoBean private DemoAccessService demoAccessService;

    @Test
    void hstsIsSentWhenTheProxyForwardedHttps() throws Exception {
        mockMvc.perform(get("/api/public/nada").header("X-Forwarded-Proto", "https"))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"));
    }

    @Test
    void hstsIsNotSentOverPlainHttp() throws Exception {
        mockMvc.perform(get("/api/public/nada")).andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @Test
    void localhostIsRejectedByCorsAndTheProductionOriginIsAllowed() throws Exception {
        mockMvc.perform(options("/api/me/reservations")
                        .header("Origin", "http://localhost:4300")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
        mockMvc.perform(options("/api/me/reservations")
                        .header("Origin", "https://www.mygym.cl")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://www.mygym.cl"));
    }

    @Test
    void theDefaultCorsOriginsInApplicationYmlHaveNoLocalhost() throws Exception {
        String yml = new String(new ClassPathResource("application.yml").getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String line = yml.lines().filter(l -> l.contains("allowed-origins:")).findFirst().orElseThrow();
        assertThat(line).doesNotContain("localhost").doesNotContain("127.0.0.1");
    }
}
