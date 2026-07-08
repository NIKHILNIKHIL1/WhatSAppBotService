package com.bot.whatsappbotservice.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bot.whatsappbotservice.auth.dto.LoginRequest;
import com.bot.whatsappbotservice.auth.dto.RefreshRequest;
import com.bot.whatsappbotservice.auth.dto.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerThenLoginThenRefreshWithRotation() throws Exception {
        RegisterRequest register = new RegisterRequest(
                "Acme Dairy", "acme-dairy", "Alice Admin", "alice@acme-dairy.test", "supersecret1",
                null, null, null, null);

        var registerResult = mvc.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register))
                .exchange();
        assertThat(registerResult).hasStatus(201);

        var loginResult = mvc.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("alice@acme-dairy.test", "supersecret1")))
                .exchange();
        assertThat(loginResult).hasStatus(200);

        String loginBody = loginResult.getResponse().getContentAsString();
        String refreshToken = objectMapper.readTree(loginBody).path("data").path("refreshToken").asText();
        String accessToken = objectMapper.readTree(loginBody).path("data").path("accessToken").asText();
        assertThat(accessToken).isNotBlank();
        assertThat(refreshToken).isNotBlank();

        RefreshRequest refreshRequest = new RefreshRequest(refreshToken);
        var refreshResult = mvc.post().uri("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest))
                .exchange();
        assertThat(refreshResult).hasStatus(200);

        // A rotated refresh token must not be usable a second time (replay protection).
        var reuseResult = mvc.post().uri("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest))
                .exchange();
        assertThat(reuseResult).hasStatus(401);
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        RegisterRequest register = new RegisterRequest(
                "Beta Grocer", "beta-grocer", "Bob Admin", "bob@beta-grocer.test", "supersecret1",
                null, null, null, null);
        mvc.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register))
                .exchange();

        var result = mvc.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("bob@beta-grocer.test", "wrong-password")))
                .exchange();

        assertThat(result).hasStatus(401);
    }

    @Test
    void duplicateSlugIsRejected() throws Exception {
        RegisterRequest first = new RegisterRequest(
                "Gamma Hardware", "gamma-hw", "Carol Admin", "carol@gamma-hw.test", "supersecret1",
                null, null, null, null);
        mvc.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(first))
                .exchange();

        RegisterRequest duplicate = new RegisterRequest(
                "Gamma Hardware 2", "gamma-hw", "Carol2", "carol2@gamma-hw.test", "supersecret1",
                null, null, null, null);
        var result = mvc.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicate))
                .exchange();

        assertThat(result).hasStatus(409);
    }
}
