package com.bot.whatsappbotservice.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bot.whatsappbotservice.catalog.ProductService;
import com.bot.whatsappbotservice.config.RateLimitingFilter;
import com.bot.whatsappbotservice.config.RequestIdFilter;
import com.bot.whatsappbotservice.customer.CustomerService;
import com.bot.whatsappbotservice.order.OrderChannel;
import com.bot.whatsappbotservice.order.OrderService;
import com.bot.whatsappbotservice.order.OrderStatus;
import com.bot.whatsappbotservice.order.dto.OrderResponse;
import com.bot.whatsappbotservice.security.JwtService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(DashboardUiController.class)
@AutoConfigureMockMvc(addFilters = false)
class DashboardUiControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ProductService productService;
    @MockitoBean
    private OrderService orderService;
    @MockitoBean
    private CustomerService customerService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private RequestIdFilter requestIdFilter;
    @MockitoBean
    private RateLimitingFilter rateLimitingFilter;

    @Test
    void dashboardRendersCountsAndRecentOrders() throws Exception {
        OrderResponse order = new OrderResponse(1L, "ORD-2026-XYZ789", 3L, "Priya Sharma", "+919876543210",
                OrderStatus.NEW, OrderChannel.WHATSAPP, "INR", new BigDecimal("99.00"), new BigDecimal("99.00"),
                null, List.of(), Instant.now());
        when(productService.list(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(customerService.list(any())).thenReturn(new PageImpl<>(List.of()));
        when(orderService.list(any(), any())).thenReturn(new PageImpl<>(List.of(order)));

        MvcTestResult result = mvc.get().uri("/ui/dashboard").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("ORD-2026-XYZ789");
    }

    @Test
    void dashboardShowsEmptyStateWithNoOrders() throws Exception {
        when(productService.list(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(customerService.list(any())).thenReturn(new PageImpl<>(List.of()));
        when(orderService.list(any(), any())).thenReturn(new PageImpl<>(List.of()));

        MvcTestResult result = mvc.get().uri("/ui/dashboard").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("No orders yet");
    }
}
