package com.bot.whatsappbotservice.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.bot.whatsappbotservice.auth.dto.RegisterRequest;
import com.bot.whatsappbotservice.catalog.dto.CreateProductRequest;
import com.bot.whatsappbotservice.customer.dto.CreateCustomerRequest;
import com.bot.whatsappbotservice.order.dto.CreateOrderRequest;
import com.bot.whatsappbotservice.order.dto.OrderItemRequest;
import com.bot.whatsappbotservice.order.dto.UpdateOrderStatusRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
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
class OrderIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void creatingOrderDeductsStockAndComputesTotal() throws Exception {
        String token = registerAndGetAccessToken("order-tenant-1", "admin@order1.test");
        long productId = createProduct(token, "SKU-ORD-1", "10.00", "50");
        long customerId = createCustomer(token, "+14155551001");

        CreateOrderRequest orderRequest = new CreateOrderRequest(
                customerId, OrderChannel.WEB, List.of(new OrderItemRequest(productId, BigDecimal.valueOf(3))),
                "please deliver in the morning", null);
        MvcTestResult orderResult = mvc.post().uri("/api/orders")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(orderRequest))
                .exchange();

        assertThat(orderResult).hasStatus(201);
        JsonNode orderData = dataNode(orderResult);
        assertThat(orderData.path("status").asText()).isEqualTo("NEW");
        assertThat(new BigDecimal(orderData.path("totalAmount").asText())).isEqualByComparingTo("30.00");

        MvcTestResult inventoryResult = mvc.get().uri("/api/inventory/{id}", productId)
                .header("Authorization", "Bearer " + token)
                .exchange();
        assertThat(new BigDecimal(dataNode(inventoryResult).path("quantityOnHand").asText()))
                .isEqualByComparingTo("47");
    }

    @Test
    void repeatingIdempotencyKeyReturnsSameOrderWithoutDoubleDeductingStock() throws Exception {
        String token = registerAndGetAccessToken("order-tenant-2", "admin@order2.test");
        long productId = createProduct(token, "SKU-ORD-2", "20.00", "10");
        long customerId = createCustomer(token, "+14155551002");

        CreateOrderRequest orderRequest = new CreateOrderRequest(
                customerId, OrderChannel.WHATSAPP, List.of(new OrderItemRequest(productId, BigDecimal.valueOf(2))),
                null, "idem-key-123");

        MvcTestResult first = mvc.post().uri("/api/orders")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(orderRequest))
                .exchange();
        MvcTestResult second = mvc.post().uri("/api/orders")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(orderRequest))
                .exchange();

        assertThat(first).hasStatus(201);
        assertThat(second).hasStatus(201);
        assertThat(dataNode(first).path("id").asLong()).isEqualTo(dataNode(second).path("id").asLong());
        assertThat(dataNode(first).path("orderNumber").asText()).isEqualTo(dataNode(second).path("orderNumber").asText());

        MvcTestResult inventoryResult = mvc.get().uri("/api/inventory/{id}", productId)
                .header("Authorization", "Bearer " + token)
                .exchange();
        assertThat(new BigDecimal(dataNode(inventoryResult).path("quantityOnHand").asText()))
                .isEqualByComparingTo("8");
    }

    @Test
    void orderCreationFailsWhenStockIsInsufficient() throws Exception {
        String token = registerAndGetAccessToken("order-tenant-3", "admin@order3.test");
        long productId = createProduct(token, "SKU-ORD-3", "5.00", "1");
        long customerId = createCustomer(token, "+14155551003");

        CreateOrderRequest orderRequest = new CreateOrderRequest(
                customerId, OrderChannel.WEB, List.of(new OrderItemRequest(productId, BigDecimal.valueOf(5))),
                null, null);
        MvcTestResult result = mvc.post().uri("/api/orders")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(orderRequest))
                .exchange();

        assertThat(result).hasStatus(422);
    }

    @Test
    void invalidStatusTransitionIsRejected() throws Exception {
        String token = registerAndGetAccessToken("order-tenant-4", "admin@order4.test");
        long productId = createProduct(token, "SKU-ORD-4", "5.00", "10");
        long customerId = createCustomer(token, "+14155551004");
        long orderId = createOrder(token, customerId, productId, 1);

        UpdateOrderStatusRequest skipAhead = new UpdateOrderStatusRequest(OrderStatus.PACKED, null);
        MvcTestResult result = mvc.patch().uri("/api/orders/{id}/status", orderId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(skipAhead))
                .exchange();

        assertThat(result).hasStatus(422);
    }

    @Test
    void cancellingOrderReleasesStockBackToInventory() throws Exception {
        String token = registerAndGetAccessToken("order-tenant-5", "admin@order5.test");
        long productId = createProduct(token, "SKU-ORD-5", "5.00", "10");
        long customerId = createCustomer(token, "+14155551005");
        long orderId = createOrder(token, customerId, productId, 4);

        UpdateOrderStatusRequest cancel = new UpdateOrderStatusRequest(OrderStatus.CANCELLED, "customer changed mind");
        MvcTestResult cancelResult = mvc.patch().uri("/api/orders/{id}/status", orderId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cancel))
                .exchange();
        assertThat(cancelResult).hasStatus(200);
        assertThat(dataNode(cancelResult).path("status").asText()).isEqualTo("CANCELLED");

        MvcTestResult inventoryResult = mvc.get().uri("/api/inventory/{id}", productId)
                .header("Authorization", "Bearer " + token)
                .exchange();
        assertThat(new BigDecimal(dataNode(inventoryResult).path("quantityOnHand").asText()))
                .isEqualByComparingTo("10");
    }

    private long createOrder(String token, long customerId, long productId, int quantity) throws Exception {
        CreateOrderRequest orderRequest = new CreateOrderRequest(
                customerId, OrderChannel.WEB, List.of(new OrderItemRequest(productId, BigDecimal.valueOf(quantity))),
                null, null);
        MvcTestResult result = mvc.post().uri("/api/orders")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(orderRequest))
                .exchange();
        return dataNode(result).path("id").asLong();
    }

    private long createProduct(String token, String sku, String price, String initialQuantity) throws Exception {
        CreateProductRequest request = new CreateProductRequest(
                sku, "Product " + sku, null, null, "pcs", new BigDecimal(price), null, null,
                new BigDecimal(initialQuantity), java.util.Map.of());
        MvcTestResult result = mvc.post().uri("/api/catalog/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .exchange();
        return dataNode(result).path("id").asLong();
    }

    private long createCustomer(String token, String phoneNumber) throws Exception {
        CreateCustomerRequest request = new CreateCustomerRequest(phoneNumber, "Test Customer", "en");
        MvcTestResult result = mvc.post().uri("/api/customers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .exchange();
        return dataNode(result).path("id").asLong();
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
