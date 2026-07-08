package com.bot.whatsappbotservice.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.bot.whatsappbotservice.auth.dto.RegisterRequest;
import com.bot.whatsappbotservice.catalog.dto.CreateProductRequest;
import com.bot.whatsappbotservice.inventory.InventoryTransactionType;
import com.bot.whatsappbotservice.inventory.dto.AdjustStockRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
class CatalogIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void creatingProductAlsoCreatesInventoryRowWithInitialQuantity() throws Exception {
        String token = registerAndGetAccessToken("catalog-tenant-1", "admin@catalog1.test");

        CreateProductRequest createProduct = new CreateProductRequest(
                "SKU-001", "Milk 1L", "Full cream milk", null, "ltr",
                new BigDecimal("55.00"), "INR", null, new BigDecimal("100"), java.util.Map.of());
        MvcTestResult createResult = mvc.post().uri("/api/catalog/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createProduct))
                .exchange();
        assertThat(createResult).hasStatus(201);
        long productId = dataNode(createResult).path("id").asLong();

        MvcTestResult inventoryResult = mvc.get().uri("/api/inventory/{id}", productId)
                .header("Authorization", "Bearer " + token)
                .exchange();
        assertThat(inventoryResult).hasStatus(200);
        assertThat(new BigDecimal(dataNode(inventoryResult).path("quantityOnHand").asText()))
                .isEqualByComparingTo("100");
    }

    @Test
    void duplicateSkuWithinTenantIsRejected() throws Exception {
        String token = registerAndGetAccessToken("catalog-tenant-2", "admin@catalog2.test");
        CreateProductRequest product = new CreateProductRequest(
                "SKU-DUP", "Item", null, null, "pcs", new BigDecimal("10.00"), null, null, null, java.util.Map.of());

        mvc.post().uri("/api/catalog/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(product))
                .exchange();

        MvcTestResult duplicateResult = mvc.post().uri("/api/catalog/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(product))
                .exchange();

        assertThat(duplicateResult).hasStatus(409);
    }

    @Test
    void adjustingStockBelowZeroIsRejected() throws Exception {
        String token = registerAndGetAccessToken("catalog-tenant-3", "admin@catalog3.test");
        CreateProductRequest product = new CreateProductRequest(
                "SKU-STOCK", "Item", null, null, "pcs", new BigDecimal("10.00"), null, null, new BigDecimal("2"),
                java.util.Map.of());
        MvcTestResult createResult = mvc.post().uri("/api/catalog/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(product))
                .exchange();
        long productId = dataNode(createResult).path("id").asLong();

        AdjustStockRequest overSale = new AdjustStockRequest(
                InventoryTransactionType.SALE, BigDecimal.valueOf(-5), "ORDER", 1L, null);
        MvcTestResult adjustResult = mvc.post().uri("/api/inventory/{id}/adjustments", productId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(overSale))
                .exchange();

        assertThat(adjustResult).hasStatus(422);
    }

    @Test
    void productsAreNotVisibleAcrossTenantsThroughTheApi() throws Exception {
        String tokenA = registerAndGetAccessToken("catalog-tenant-4a", "admin@catalog4a.test");
        String tokenB = registerAndGetAccessToken("catalog-tenant-4b", "admin@catalog4b.test");

        CreateProductRequest product = new CreateProductRequest(
                "SKU-SHARED", "Only in tenant A", null, null, "pcs", new BigDecimal("1.00"), null, null, null,
                java.util.Map.of());
        MvcTestResult createResult = mvc.post().uri("/api/catalog/products")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(product))
                .exchange();
        long productId = dataNode(createResult).path("id").asLong();

        MvcTestResult getFromOtherTenant = mvc.get().uri("/api/catalog/products/{id}", productId)
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
