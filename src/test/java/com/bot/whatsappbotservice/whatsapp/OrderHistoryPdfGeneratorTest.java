package com.bot.whatsappbotservice.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import com.bot.whatsappbotservice.customer.Customer;
import com.bot.whatsappbotservice.i18n.WhatsAppMessages;
import com.bot.whatsappbotservice.order.OrderChannel;
import com.bot.whatsappbotservice.order.OrderStatus;
import com.bot.whatsappbotservice.order.dto.OrderResponse;
import com.bot.whatsappbotservice.tenant.Tenant;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

class OrderHistoryPdfGeneratorTest {

    private OrderHistoryPdfGenerator generator;
    private Tenant tenant;
    private Customer customer;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("i18n/messages");
        messageSource.setDefaultEncoding("UTF-8");
        generator = new OrderHistoryPdfGenerator(new WhatsAppMessages(messageSource));

        tenant = new Tenant();
        tenant.setId(1L);
        tenant.setCurrencyCode("INR");

        customer = new Customer();
        customer.setId(9L);
        customer.setPhoneNumber("+14155550100");
    }

    @Test
    void generatesValidPdfBytesForOrderList() {
        List<OrderResponse> orders = List.of(
                new OrderResponse(1L, "ORD-1", customer.getId(), null, customer.getPhoneNumber(),
                        OrderStatus.DELIVERED, OrderChannel.WHATSAPP, "INR", new BigDecimal("100.00"),
                        new BigDecimal("100.00"), null, List.of(), Instant.now()),
                new OrderResponse(2L, "ORD-2", customer.getId(), null, customer.getPhoneNumber(),
                        OrderStatus.NEW, OrderChannel.WHATSAPP, "INR", new BigDecimal("50.00"),
                        new BigDecimal("50.00"), null, List.of(), Instant.now()));

        byte[] pdf = generator.generate(tenant, customer, orders, "Last 7 days", "en");

        assertThat(pdf).isNotEmpty();
        // First bytes of every valid PDF file are the "%PDF" magic header.
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    void generatesValidPdfBytesForEmptyOrderList() {
        byte[] pdf = generator.generate(tenant, customer, List.of(), "Last 30 days", "en");

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }
}
