package com.library.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration test through the full Spring context (real security
 * filters, real JPA/H2 persistence, real BCrypt hashing) covering the
 * register -&gt; login -&gt; reject-bad-password -&gt; reject-duplicate-username flow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void register_thenLogin_returnsWorkingJwt() throws Exception {
        String registerBody = objectMapper.writeValueAsString(
                new RegisterRequest("carol", "carol@example.com", "SuperSecret1"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.username").value("carol"))
                .andExpect(jsonPath("$.role").value("USER"));

        String loginBody = objectMapper.writeValueAsString(new LoginRequest("carol", "SuperSecret1"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void login_returnsUnauthorized_forWrongPassword() throws Exception {
        String registerBody = objectMapper.writeValueAsString(
                new RegisterRequest("dave", "dave@example.com", "CorrectPass1"));
        mockMvc.perform(post("/api/auth/register").contentType("application/json").content(registerBody))
                .andExpect(status().isCreated());

        String badLogin = objectMapper.writeValueAsString(new LoginRequest("dave", "WrongPassword"));
        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(badLogin))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_returnsConflict_forDuplicateUsername() throws Exception {
        String body = objectMapper.writeValueAsString(
                new RegisterRequest("erin", "erin@example.com", "SomePassword1"));

        mockMvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void register_returnsBadRequest_whenPasswordTooShort() throws Exception {
        String body = objectMapper.writeValueAsString(
                new RegisterRequest("frank", "frank@example.com", "short"));

        mockMvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").isArray());
    }
}
