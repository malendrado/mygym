package com.cortesdev.mygym.security;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cortesdev.mygym.controllers.GymAdminController;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.models.dto.GymResponse;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.services.DemoAccessService;
import com.cortesdev.mygym.services.GymCapacityService;
import com.cortesdev.mygym.services.GymClosureService;
import com.cortesdev.mygym.services.GymService;
import com.cortesdev.mygym.services.ReservationService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Qué puede leer cada rol bajo /api/gym-admin (auditoría 2026-10-17: el PROFESOR leía los datos bancarios y tributarios
 * del dueño y la lista de administradores). Usa la configuración de seguridad REAL con tokens firmados de verdad, sin base
 * de datos: una ruta sin controlador responde 404 cuando la autorización la deja pasar y 403 cuando no.
 */
@WebMvcTest(GymAdminController.class)
@Import({SecurityConfig.class, JwtService.class, DemoAccessExpiryFilter.class, UserSessionFilter.class,
        GymPauseFilter.class, AuthRateLimitFilter.class})
@TestPropertySource(properties = {
    "app.jwt.secret=una-clave-de-prueba-de-al-menos-32-caracteres!!",
    "app.cors.allowed-origins=http://localhost:4300"
})
class GymAdminReadAccessTest {

    private static final long GYM_ID = 5L;

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;

    @MockitoBean private GymService gymService;
    @MockitoBean private ReservationService reservationService;
    @MockitoBean private GymClosureService gymClosureService;
    @MockitoBean private GymCapacityService gymCapacityService;
    @MockitoBean private UserSessionGuard userSessionGuard;
    @MockitoBean private GymPauseGuard gymPauseGuard;
    @MockitoBean private AppUserRepository appUserRepository;
    @MockitoBean private DemoAccessService demoAccessService;

    @BeforeEach
    void sessionsAreValidAndTheGymIsActive() {
        when(userSessionGuard.isValid(anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);
        when(gymPauseGuard.isPaused(anyLong())).thenReturn(false);
        // DemoAccessExpiryFilter busca al usuario DEMO_ADMIN: que exista y su acceso no haya vencido.
        com.cortesdev.mygym.models.AppUser demo = com.cortesdev.mygym.models.AppUser.builder().id(7L).build();
        when(appUserRepository.findById(7L)).thenReturn(java.util.Optional.of(demo));
        when(demoAccessService.expireIfNeeded(demo)).thenReturn(false);
    }

    private String bearer(Role role) {
        return "Bearer " + jwtService.issueToken(7L, "u@x.cl", role, GYM_ID);
    }

    private void assertGet(String path, Role role, int expectedStatus) throws Exception {
        mockMvc.perform(get(path).header("Authorization", bearer(role))).andExpect(status().is(expectedStatus));
    }

    @Test
    void theTeacherCannotReadWhoAdministersTheGymNorTheCompanyAndFlowData() throws Exception {
        for (String path : new String[] {
            "/api/gym-admin/gym/flow-account",
            "/api/gym-admin/gym/admins",
            "/api/gym-admin/profesores",
            "/api/gym-admin/members/emails",
            "/api/gym-admin/members/12/payment-preview",
        }) {
            assertGet(path, Role.PROFESOR, 403);
        }
    }

    @Test
    void theGymAdminAndTheDemoAdminStillReachThoseReads() throws Exception {
        // Sin controlador para estas rutas en este slice: 404 = la autorización los dejó pasar.
        for (String path : new String[] {"/api/gym-admin/profesores", "/api/gym-admin/members/emails"}) {
            assertGet(path, Role.GYM_ADMIN, 404);
            assertGet(path, Role.DEMO_ADMIN, 404);
        }
        assertGet("/api/gym-admin/gym/flow-account", Role.GYM_ADMIN, 200);
        assertGet("/api/gym-admin/gym/flow-account", Role.DEMO_ADMIN, 200);
        assertGet("/api/gym-admin/gym/admins", Role.GYM_ADMIN, 200);
    }

    @Test
    void theTeacherKeepsTheReadsItsPanelUses() throws Exception {
        assertGet("/api/gym-admin/gym/limits", Role.PROFESOR, 200);
        assertGet("/api/gym-admin/gym/agenda?from=2026-10-12&to=2026-10-18", Role.PROFESOR, 200);
        assertGet("/api/gym-admin/members", Role.PROFESOR, 404); // sin controlador acá: pasó la autorización
    }

    @Test
    void theDemoPreviewIsOnlyForTheDemoAdmin() throws Exception {
        assertGet("/api/gym-admin/demo-preview/gym", Role.GYM_ADMIN, 403);
        assertGet("/api/gym-admin/demo-preview/gym", Role.PROFESOR, 403);
        assertGet("/api/gym-admin/demo-preview/gym", Role.DEMO_ADMIN, 404);
    }

    @Test
    void theGymCardForATeacherCarriesNoBankAccountButTheAdminSeesIt() throws Exception {
        GymResponse card = new GymResponse(
                GYM_ID, "pub", "XaloGym", "xalogym", true, 100, true, "#c6ff3d", "DARK", null, null, null, null, null,
                30, 120, 30, true, false, "Banco de Chile", "Cuenta corriente", "00123456789", "12.345.678-5",
                "Dueña del gimnasio", "pagos@xalogym.cl", Instant.now(), Instant.now());
        when(gymService.getGym(GYM_ID)).thenReturn(card);

        mockMvc.perform(get("/api/gym-admin/gym").header("Authorization", bearer(Role.PROFESOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("XaloGym"))
                .andExpect(jsonPath("$.bankAccountNumber").doesNotExist())
                .andExpect(jsonPath("$.bankHolderRut").doesNotExist())
                .andExpect(jsonPath("$.bankHolderName").doesNotExist())
                .andExpect(jsonPath("$.bankConfirmationEmail").doesNotExist())
                .andExpect(jsonPath("$.bankName").doesNotExist());

        mockMvc.perform(get("/api/gym-admin/gym").header("Authorization", bearer(Role.GYM_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankAccountNumber").value("00123456789"));
    }
}
