package com.bot.whatsappbotservice.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.bot.whatsappbotservice.auth.dto.RegisterRequest;
import com.bot.whatsappbotservice.customer.dto.CreateCustomerRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class CustomerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsCustomerSuccessfully() throws Exception {
        String token = registerAndGetAccessToken("customer-tenant-1", "admin@customer1.test");

        CreateCustomerRequest request = new CreateCustomerRequest("+14155550001", "Jane Doe", "en");
        MvcTestResult result = mvc.post().uri("/api/customers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .exchange();

        assertThat(result).hasStatus(201);
        assertThat(dataNode(result).path("phoneNumber").asText()).isEqualTo("+14155550001");
        assertThat(dataNode(result).path("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void rejectsInvalidPhoneNumberFormat() throws Exception {
        String token = registerAndGetAccessToken("customer-tenant-2", "admin@customer2.test");

        CreateCustomerRequest request = new CreateCustomerRequest("not-a-phone-number", "Jane Doe", "en");
        MvcTestResult result = mvc.post().uri("/api/customers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .exchange();

        assertThat(result).hasStatus(400);
    }

    @Test
    void duplicatePhoneNumberWithinTenantIsRejected() throws Exception {
        String token = registerAndGetAccessToken("customer-tenant-3", "admin@customer3.test");
        CreateCustomerRequest request = new CreateCustomerRequest("+14155550002", "Jane Doe", "en");

        mvc.post().uri("/api/customers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .exchange();

        MvcTestResult duplicateResult = mvc.post().uri("/api/customers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .exchange();

        assertThat(duplicateResult).hasStatus(409);
    }

    @Test
    void customersAreNotVisibleAcrossTenants() throws Exception {
        String tokenA = registerAndGetAccessToken("customer-tenant-4a", "admin@customer4a.test");
        String tokenB = registerAndGetAccessToken("customer-tenant-4b", "admin@customer4b.test");

        CreateCustomerRequest request = new CreateCustomerRequest("+14155550003", "Only in tenant A", "en");
        MvcTestResult createResult = mvc.post().uri("/api/customers")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .exchange();
        long customerId = dataNode(createResult).path("id").asLong();

        MvcTestResult getFromOtherTenant = mvc.get().uri("/api/customers/{id}", customerId)
                .header("Authorization", "Bearer " + tokenB)
                .exchange();

        assertThat(getFromOtherTenant).hasStatus(404);
    }

    private String registerAndGetAccessToken(String slug, String email) throws Exception {
        RegisterRequest register = new RegisterRequest(
                "Tenant " + slug, slug, "Admin", email, "supersecret1", null, null, null, null);
        MvcTestResult result = mvc.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register))
                .exchange();
        return dataNode(result).path("accessToken").asText();
    }

    private JsonNode dataNode(MvcTestResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
}
