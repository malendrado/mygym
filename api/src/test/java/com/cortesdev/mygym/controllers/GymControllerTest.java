package com.cortesdev.mygym.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class GymControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    private String superAdminToken() {
        return jwtService.issueToken(1L, "super-admin-" + System.nanoTime() + "@mygym.test", Role.SUPER_ADMIN, null);
    }

    private String ownerEmail() {
        return "owner-" + System.nanoTime() + "@mygym.test";
    }

    @Test
    void createGym_withoutToken_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(
                new CreateGymPayload("No Auth Gym", "no-auth-gym-" + System.nanoTime(), 10, "Owner", ownerEmail()));

        mockMvc.perform(post("/api/gyms").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createGym_happyPath_returns201WithLocationHeader() throws Exception {
        String body = objectMapper.writeValueAsString(
                new CreateGymPayload("Gold Gym", "gold-gym-" + System.nanoTime(), 100, "Owner", ownerEmail()));

        mockMvc.perform(post("/api/gyms")
                        .header("Authorization", "Bearer " + superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/gyms/")))
                .andExpect(jsonPath("$.name").value("Gold Gym"));
    }

    @Test
    void createGym_duplicateSlug_returns409() throws Exception {
        String slug = "dup-gym-" + System.nanoTime();
        String body =
                objectMapper.writeValueAsString(new CreateGymPayload("Dup Gym", slug, 10, "Owner", ownerEmail()));
        String token = superAdminToken();

        mockMvc.perform(post("/api/gyms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        String duplicateSlugBody = objectMapper.writeValueAsString(
                new CreateGymPayload("Dup Gym Again", slug, 10, "Owner", ownerEmail()));

        mockMvc.perform(post("/api/gyms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateSlugBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate slug"));
    }

    @Test
    void createGym_duplicateOwnerEmail_returns409() throws Exception {
        String email = ownerEmail();
        String token = superAdminToken();
        String firstBody = objectMapper.writeValueAsString(
                new CreateGymPayload("First Gym", "first-gym-" + System.nanoTime(), 10, "Owner", email));

        mockMvc.perform(post("/api/gyms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstBody))
                .andExpect(status().isCreated());

        String secondBody = objectMapper.writeValueAsString(
                new CreateGymPayload("Second Gym", "second-gym-" + System.nanoTime(), 10, "Owner", email));

        mockMvc.perform(post("/api/gyms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate owner email"));
    }

    @Test
    void getGym_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/gyms/999999").header("Authorization", "Bearer " + superAdminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Gym not found"));
    }

    @Test
    void createGym_blankName_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                new CreateGymPayload("", "blank-name-gym", 10, "Owner", ownerEmail()));

        mockMvc.perform(post("/api/gyms")
                        .header("Authorization", "Bearer " + superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void addBlock_invalidTimeRange_returns400() throws Exception {
        String token = superAdminToken();
        String gymBody = objectMapper.writeValueAsString(new CreateGymPayload(
                "Block Gym", "block-gym-" + System.nanoTime(), 10, "Owner", ownerEmail()));
        String response = mockMvc.perform(post("/api/gyms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gymBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long gymId = objectMapper.readTree(response).get("id").asLong();

        String blockBody = objectMapper.writeValueAsString(
                new CreateBlockPayload("Evening", "MONDAY", "20:00", "19:00", 20));

        mockMvc.perform(post("/api/gyms/" + gymId + "/blocks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blockBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid block schedule"));
    }

    @Test
    void listAdmins_afterCreate_returnsInitialAdmin() throws Exception {
        String email = ownerEmail();
        String token = superAdminToken();
        String body = objectMapper.writeValueAsString(
                new CreateGymPayload("Admins Gym", "admins-gym-" + System.nanoTime(), 10, "Owner", email));
        String response = mockMvc.perform(post("/api/gyms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long gymId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/gyms/" + gymId + "/admins").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value(email))
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void addAdmin_happyPath_returns201() throws Exception {
        String token = superAdminToken();
        String gymBody = objectMapper.writeValueAsString(new CreateGymPayload(
                "Multi Admin Gym", "multi-admin-gym-" + System.nanoTime(), 10, "Owner", ownerEmail()));
        String gymResponse = mockMvc.perform(post("/api/gyms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gymBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long gymId = objectMapper.readTree(gymResponse).get("id").asLong();

        String secondAdminEmail = ownerEmail();
        String adminBody = objectMapper.writeValueAsString(new AdminPayload("Second Admin", secondAdminEmail));

        mockMvc.perform(post("/api/gyms/" + gymId + "/admins")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(secondAdminEmail))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(get("/api/gyms/" + gymId + "/admins").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void addAdmin_duplicateEmail_returns409() throws Exception {
        String token = superAdminToken();
        String email = ownerEmail();
        String gymBody = objectMapper.writeValueAsString(
                new CreateGymPayload("Dup Admin Gym", "dup-admin-gym-" + System.nanoTime(), 10, "Owner", email));
        String gymResponse = mockMvc.perform(post("/api/gyms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gymBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long gymId = objectMapper.readTree(gymResponse).get("id").asLong();

        String adminBody = objectMapper.writeValueAsString(new AdminPayload("Duplicate", email));

        mockMvc.perform(post("/api/gyms/" + gymId + "/admins")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate owner email"));
    }

    @Test
    void updateAdminStatus_deactivate_returnsUpdatedAdmin() throws Exception {
        String token = superAdminToken();
        String gymBody = objectMapper.writeValueAsString(new CreateGymPayload(
                "Status Gym", "status-gym-" + System.nanoTime(), 10, "Owner", ownerEmail()));
        String gymResponse = mockMvc.perform(post("/api/gyms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gymBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long gymId = objectMapper.readTree(gymResponse).get("id").asLong();

        String adminsResponse = mockMvc.perform(
                        get("/api/gyms/" + gymId + "/admins").header("Authorization", "Bearer " + token))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long adminId = objectMapper.readTree(adminsResponse).get(0).get("id").asLong();

        mockMvc.perform(put("/api/gyms/" + gymId + "/admins/" + adminId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminStatusPayload(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void updateAdminStatus_wrongGym_returns404() throws Exception {
        String token = superAdminToken();
        String firstGymBody = objectMapper.writeValueAsString(new CreateGymPayload(
                "Gym A", "gym-a-" + System.nanoTime(), 10, "Owner", ownerEmail()));
        String firstGymResponse = mockMvc.perform(post("/api/gyms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstGymBody))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long gymAId = objectMapper.readTree(firstGymResponse).get("id").asLong();
        String adminsResponse = mockMvc.perform(
                        get("/api/gyms/" + gymAId + "/admins").header("Authorization", "Bearer " + token))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long adminId = objectMapper.readTree(adminsResponse).get(0).get("id").asLong();

        String secondGymBody = objectMapper.writeValueAsString(new CreateGymPayload(
                "Gym B", "gym-b-" + System.nanoTime(), 10, "Owner", ownerEmail()));
        String secondGymResponse = mockMvc.perform(post("/api/gyms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondGymBody))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long gymBId = objectMapper.readTree(secondGymResponse).get("id").asLong();

        mockMvc.perform(put("/api/gyms/" + gymBId + "/admins/" + adminId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminStatusPayload(false))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Admin not found"));
    }

    private record CreateGymPayload(
            String name, String slug, Integer maxUsers, String ownerName, String ownerEmail) {}

    private record CreateBlockPayload(
            String label, String dayOfWeek, String startTime, String endTime, Integer capacity) {}

    private record AdminPayload(String name, String email) {}

    private record AdminStatusPayload(Boolean active) {}
}
