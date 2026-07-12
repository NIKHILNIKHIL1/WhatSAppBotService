package com.bot.whatsappbotservice.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.bot.whatsappbotservice.catalog.ProductService;
import com.bot.whatsappbotservice.catalog.dto.ProductResponse;
import com.bot.whatsappbotservice.common.exception.BusinessRuleViolationException;
import com.bot.whatsappbotservice.config.RateLimitingFilter;
import com.bot.whatsappbotservice.config.RequestIdFilter;
import com.bot.whatsappbotservice.customer.CustomerService;
import com.bot.whatsappbotservice.customer.dto.CustomerResponse;
import com.bot.whatsappbotservice.customer.CustomerStatus;
import com.bot.whatsappbotservice.order.OrderChannel;
import com.bot.whatsappbotservice.order.OrderService;
import com.bot.whatsappbotservice.order.OrderStatus;
import com.bot.whatsappbotservice.order.dto.OrderItemResponse;
import com.bot.whatsappbotservice.order.dto.OrderResponse;
import com.bot.whatsappbotservice.order.dto.OrderStatusHistoryResponse;
import com.bot.whatsappbotservice.security.JwtService;
import com.bot.whatsappbotservice.tenant.TenantRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(OrderUiController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderUiControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private OrderService orderService;
    @MockitoBean
    private CustomerService customerService;
    @MockitoBean
    private ProductService productService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private RequestIdFilter requestIdFilter;
    @MockitoBean
    private RateLimitingFilter rateLimitingFilter;
    // UiModelAttributesAdvice (@ControllerAdvice over all of com.bot.whatsappbotservice.ui) needs
    // this on every UI slice test; without it the whole context fails to load.
    @MockitoBean
    private TenantRepository tenantRepository;

    private OrderResponse sampleOrder(OrderStatus status) {
        OrderItemResponse item = new OrderItemResponse(1L, 2L, "Milk 1L", new BigDecimal("55.00"),
                BigDecimal.valueOf(2), new BigDecimal("110.00"));
        return new OrderResponse(1L, "ORD-2026-ABC123", 9L, "Priya Sharma", "+919876543210", status,
                OrderChannel.WEB, "INR", new BigDecimal("110.00"), new BigDecimal("110.00"),
                com.bot.whatsappbotservice.order.PaymentStatus.UNPAID, null, BigDecimal.ZERO, null, null,
                "please hurry", List.of(item), Instant.now());
    }

    @Test
    void listRendersOrdersTable() throws Exception {
        when(orderService.list(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleOrder(OrderStatus.NEW))));

        MvcTestResult result = mvc.get().uri("/ui/orders").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString())
                .contains("ORD-2026-ABC123").contains("Priya Sharma").contains("+919876543210");
    }

    @Test
    void detailRendersItemsAndStatusForm() throws Exception {
        OrderResponse order = sampleOrder(OrderStatus.NEW);
        when(orderService.get(1L)).thenReturn(order);
        when(orderService.history(eq(1L), any())).thenReturn(new PageImpl<>(List.of(
                new OrderStatusHistoryResponse(1L, null, OrderStatus.NEW, "Order created", Instant.now()))));

        MvcTestResult result = mvc.get().uri("/ui/orders/1").exchange();

        assertThat(result).hasStatusOk();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Milk 1L").contains("Update Status").contains("Priya Sharma").contains("+919876543210");
    }

    @Test
    void detailHidesStatusFormForTerminalOrder() throws Exception {
        OrderResponse order = sampleOrder(OrderStatus.DELIVERED);
        when(orderService.get(1L)).thenReturn(order);
        when(orderService.history(eq(1L), any())).thenReturn(new PageImpl<>(List.of()));

        MvcTestResult result = mvc.get().uri("/ui/orders/1").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("Update Status");
    }

    @Test
    void newFormRendersCustomerAndProductOptions() throws Exception {
        CustomerResponse customer = new CustomerResponse(9L, "+14155550100", "Jane Doe", "en", CustomerStatus.ACTIVE);
        ProductResponse product = new ProductResponse(2L, "SKU-1", "Milk 1L", null, null, "ltr",
                new BigDecimal("55.00"), "INR", null, true, java.util.Map.of());
        when(customerService.list(any())).thenReturn(new PageImpl<>(List.of(customer)));
        when(productService.list(any(), any())).thenReturn(new PageImpl<>(List.of(product)));

        MvcTestResult result = mvc.get().uri("/ui/orders/new").exchange();

        assertThat(result).hasStatusOk();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Jane Doe").contains("Milk 1L");
    }

    @Test
    void submittingOrderWithNoItemsReRendersFormWithError() throws Exception {
        when(customerService.list(any())).thenReturn(new PageImpl<>(List.of()));
        when(productService.list(any(), any())).thenReturn(new PageImpl<>(List.of()));

        MvcTestResult result = mvc.post().uri("/ui/orders/new")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("customerId", "9")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("field-error");
    }

    @Test
    void invalidStatusTransitionRedirectsWithErrorFlash() {
        doThrow(new BusinessRuleViolationException("Cannot transition order from DELIVERED to NEW"))
                .when(orderService).updateStatus(ArgumentMatchers.eq(1L), ArgumentMatchers.any());

        MvcTestResult result = mvc.post().uri("/ui/orders/1/status")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("status", "NEW")
                .exchange();

        assertThat(result).hasStatus3xxRedirection();
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/ui/orders/1");
    }
}
