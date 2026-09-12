package com.cortesdev.mygym.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void loginWithGoogle_blankIdToken_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(new GoogleLoginPayload(""));

        mockMvc.perform(post("/api/auth/google").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    // Note: verifying an actual Google ID token requires reaching Google's OIDC discovery
    // endpoint over the network (see GoogleTokenVerifier). That path is intentionally not
    // covered by an automated test here to avoid making this suite depend on outbound network
    // access; it should be exercised manually against a real Google Sign-In credential.

    private record GoogleLoginPayload(String idToken) {}
}
