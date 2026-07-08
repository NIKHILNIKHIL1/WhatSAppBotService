package com.bot.whatsappbotservice.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.bot.whatsappbotservice.common.exception.BusinessRuleViolationException;
import com.bot.whatsappbotservice.config.RateLimitingFilter;
import com.bot.whatsappbotservice.config.RequestIdFilter;
import com.bot.whatsappbotservice.inventory.InventoryService;
import com.bot.whatsappbotservice.inventory.dto.InventoryOverviewResponse;
import com.bot.whatsappbotservice.inventory.dto.InventoryResponse;
import com.bot.whatsappbotservice.security.JwtService;
import java.math.BigDecimal;
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

@WebMvcTest(InventoryUiController.class)
@AutoConfigureMockMvc(addFilters = false)
class InventoryUiControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private InventoryService inventoryService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private RequestIdFilter requestIdFilter;
    @MockitoBean
    private RateLimitingFilter rateLimitingFilter;

    @Test
    void listRendersInventoryOverview() throws Exception {
        InventoryOverviewResponse item = new InventoryOverviewResponse(1L, "SKU-1", "Milk 1L",
                new BigDecimal("42"), BigDecimal.TEN);
        when(inventoryService.listWithProducts(any())).thenReturn(new PageImpl<>(List.of(item)));

        MvcTestResult result = mvc.get().uri("/ui/inventory").exchange();

        assertThat(result).hasStatusOk();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("SKU-1").contains("Milk 1L").contains("42");
    }

    @Test
    void detailRendersAdjustmentForm() throws Exception {
        InventoryResponse inventory = new InventoryResponse(1L, 5L, new BigDecimal("42"), BigDecimal.TEN);
        when(inventoryService.get(5L)).thenReturn(inventory);

        MvcTestResult result = mvc.get().uri("/ui/inventory/5").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("Adjust Stock");
    }

    @Test
    void insufficientStockReRendersDetailWithError() throws Exception {
        InventoryResponse inventory = new InventoryResponse(1L, 5L, new BigDecimal("2"), BigDecimal.TEN);
        when(inventoryService.get(5L)).thenReturn(inventory);
        doThrow(new BusinessRuleViolationException("Insufficient stock for product 5"))
                .when(inventoryService).adjustStock(eq(5L), ArgumentMatchers.any());

        MvcTestResult result = mvc.post().uri("/ui/inventory/5/adjust")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("transactionType", "SALE")
                .param("quantityDelta", "-10")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("Insufficient stock");
    }

    @Test
    void historyRendersTransactionsTable() throws Exception {
        InventoryResponse inventory = new InventoryResponse(1L, 5L, new BigDecimal("42"), BigDecimal.TEN);
        when(inventoryService.get(5L)).thenReturn(inventory);
        when(inventoryService.history(eq(5L), any())).thenReturn(new PageImpl<>(List.of()));

        MvcTestResult result = mvc.get().uri("/ui/inventory/5/history").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("No stock movements yet");
    }
}
