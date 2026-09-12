package com.cortesdev.mygym.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cortesdev.mygym.models.AppUser;
import com.cortesdev.mygym.models.Gym;
import com.cortesdev.mygym.models.Role;
import com.cortesdev.mygym.repositories.AppUserRepository;
import com.cortesdev.mygym.repositories.GymRepository;
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
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GymRepository gymRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    void createMember_asGymAdmin_returns201ScopedToOwnGym() throws Exception {
        Gym gym = gymRepository.save(Gym.builder()
                .name("Member Gym")
                .slug("member-gym-" + System.nanoTime())
                .maxUsers(50)
                .active(true)
                .googleLoginEnabled(true)
                .build());
        AppUser admin = appUserRepository.save(AppUser.builder()
                .email("admin-" + System.nanoTime() + "@test.com")
                .name("Admin")
                .role(Role.GYM_ADMIN)
                .gymId(gym.getId())
                .active(true)
                .build());
        String token = jwtService.issueToken(admin.getId(), admin.getEmail(), Role.GYM_ADMIN, gym.getId());

        String body = objectMapper.writeValueAsString(
                new MemberPayload("Socio Uno", "socio-" + System.nanoTime() + "@test.com"));

        mockMvc.perform(post("/api/gym-admin/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gymId").value(gym.getId()));
    }

    @Test
    void createMember_withoutToken_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(new MemberPayload("Nadie", "nadie-" + System.nanoTime() + "@test.com"));

        mockMvc.perform(post("/api/gym-admin/members").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createMember_asMemberRole_returns403() throws Exception {
        Gym gym = gymRepository.save(Gym.builder()
                .name("Forbidden Gym")
                .slug("forbidden-gym-" + System.nanoTime())
                .maxUsers(10)
                .active(true)
                .googleLoginEnabled(true)
                .build());
        String token = jwtService.issueToken(1L, "socio-" + System.nanoTime() + "@test.com", Role.MEMBER, gym.getId());
        String body = objectMapper.writeValueAsString(new MemberPayload("X", "x-" + System.nanoTime() + "@test.com"));

        mockMvc.perform(post("/api/gym-admin/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    private record MemberPayload(String name, String email) {}
}
