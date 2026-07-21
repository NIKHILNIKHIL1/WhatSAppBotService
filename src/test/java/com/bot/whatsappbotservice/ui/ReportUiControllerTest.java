package com.bot.whatsappbotservice.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bot.whatsappbotservice.config.RateLimitingFilter;
import com.bot.whatsappbotservice.config.RequestIdFilter;
import com.bot.whatsappbotservice.report.ReportService;
import com.bot.whatsappbotservice.report.dto.DateRange;
import com.bot.whatsappbotservice.report.dto.PaymentSummary;
import com.bot.whatsappbotservice.report.dto.ReportOverview;
import com.bot.whatsappbotservice.security.JwtService;
import com.bot.whatsappbotservice.tenant.TenantRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(ReportUiController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReportUiControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean private ReportService reportService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private RequestIdFilter requestIdFilter;
    @MockitoBean private RateLimitingFilter rateLimitingFilter;
    // UiModelAttributesAdvice (@ControllerAdvice over com.bot.whatsappbotservice.ui) requires this.
    @MockitoBean private TenantRepository tenantRepository;

    private static final DateRange SAMPLE_RANGE = DateRange.of(
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 12), ZoneId.of("UTC"));

    private static final ReportOverview EMPTY_OVERVIEW = new ReportOverview(
            BigDecimal.ZERO, 0L, BigDecimal.ZERO, BigDecimal.ZERO,
            0L, 0L, 0L, BigDecimal.ZERO, 0L, null);

    private static final PaymentSummary EMPTY_PAYMENT = new PaymentSummary(
            BigDecimal.ZERO, BigDecimal.ZERO, 0L, BigDecimal.ZERO);

    @BeforeEach
    void stubReportService() {
        when(reportService.resolveDateRange(any(), any())).thenReturn(SAMPLE_RANGE);
        when(reportService.getCurrencyCode()).thenReturn("INR");
        when(reportService.getOverview(any())).thenReturn(EMPTY_OVERVIEW);
        when(reportService.getDailyRevenue(any())).thenReturn(List.of());
        when(reportService.getProductPerformance(any())).thenReturn(List.of());
        when(reportService.getTopCustomers(any())).thenReturn(List.of());
        when(reportService.getInventoryMovements(any())).thenReturn(List.of());
        when(reportService.getOrderStatusCounts(any())).thenReturn(Map.of());
        when(reportService.getPaymentSummary(any())).thenReturn(EMPTY_PAYMENT);
        when(reportService.getOutstandingReceivables()).thenReturn(List.of());
        when(reportService.getEngagementSummary(any())).thenReturn(List.of());
    }

    // ─── Overview page ───────────────────────────────────────────────────────

    @Test
    void overview_returnsOk() {
        assertThat(mvc.get().uri("/ui/reports").exchange()).hasStatusOk();
    }

    @Test
    void overview_rendersDateRangeLabel() throws Exception {
        assertThat(mvc.get().uri("/ui/reports").exchange()
                .getResponse().getContentAsString())
                .contains("01 Jul 2026")
                .contains("12 Jul 2026");
    }

    @Test
    void overview_withExplicitDateParams_returnsOk() {
        assertThat(mvc.get().uri("/ui/reports?from=2026-07-01&to=2026-07-12").exchange())
                .hasStatusOk();
    }

    @Test
    void overview_rendersSubReportNavLinks() throws Exception {
        String body = mvc.get().uri("/ui/reports").exchange()
                .getResponse().getContentAsString();
        assertThat(body)
                .contains("/ui/reports/revenue")
                .contains("/ui/reports/products")
                .contains("/ui/reports/customers")
                .contains("/ui/reports/inventory")
                .contains("/ui/reports/orders")
                .contains("/ui/reports/payments")
                .contains("/ui/reports/whatsapp");
    }

    @Test
    void overview_rendersDateFilterPresets() throws Exception {
        String body = mvc.get().uri("/ui/reports").exchange()
                .getResponse().getContentAsString();
        assertThat(body)
                .contains("Today")
                .contains("Yesterday")
                .contains("Last 7 days")
                .contains("This month");
    }

    @Test
    void overview_rendersKpiTiles() throws Exception {
        String body = mvc.get().uri("/ui/reports").exchange()
                .getResponse().getContentAsString();
        assertThat(body)
                .contains("Revenue")
                .contains("Orders")
                .contains("Avg Order Value")
                .contains("Outstanding");
    }

    @Test
    void overview_rendersDayCountInPeriodLabel() throws Exception {
        assertThat(mvc.get().uri("/ui/reports").exchange()
                .getResponse().getContentAsString()).contains("12");
    }

    // ─── Sub-report pages — HTTP status ──────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"/revenue", "/products", "/customers", "/inventory",
                             "/orders", "/payments", "/whatsapp"})
    void subReport_returnsOk(String path) {
        assertThat(mvc.get().uri("/ui/reports" + path).exchange()).hasStatusOk();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/revenue", "/products", "/customers", "/inventory",
                             "/orders", "/payments", "/whatsapp"})
    void subReport_rendersDateRange(String path) throws Exception {
        assertThat(mvc.get().uri("/ui/reports" + path).exchange()
                .getResponse().getContentAsString())
                .contains("01 Jul 2026")
                .contains("12 Jul 2026");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/revenue", "/products", "/customers", "/inventory",
                             "/orders", "/payments", "/whatsapp"})
    void subReport_rendersBackLink(String path) throws Exception {
        assertThat(mvc.get().uri("/ui/reports" + path).exchange()
                .getResponse().getContentAsString())
                .contains("/ui/reports");
    }

    @Test
    void subReport_withDateParams_returnsOk() {
        assertThat(mvc.get()
                .uri("/ui/reports/revenue?from=2026-07-01&to=2026-07-12").exchange())
                .hasStatusOk();
    }

    // ─── Sub-report pages — empty state ──────────────────────────────────────

    @Test
    void revenuePage_rendersEmptyState() throws Exception {
        assertThat(mvc.get().uri("/ui/reports/revenue").exchange()
                .getResponse().getContentAsString())
                .contains("No orders in this period");
    }

    @Test
    void productsPage_rendersEmptyState() throws Exception {
        assertThat(mvc.get().uri("/ui/reports/products").exchange()
                .getResponse().getContentAsString())
                .contains("No sales in this period");
    }

    @Test
    void paymentsPage_rendersKpiTiles() throws Exception {
        String body = mvc.get().uri("/ui/reports/payments").exchange()
                .getResponse().getContentAsString();
        assertThat(body)
                .contains("Billed")
                .contains("Collected")
                .contains("Collection Rate")
                .contains("Outstanding");
    }

    @Test
    void whatsappPage_rendersEmptyState() throws Exception {
        assertThat(mvc.get().uri("/ui/reports/whatsapp").exchange()
                .getResponse().getContentAsString())
                .contains("No WhatsApp messages in this period");
    }

    // ─── CSV export ──────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"/revenue", "/products", "/customers", "/inventory",
                             "/orders", "/payments", "/whatsapp"})
    void export_returnsCsvContentType(String path) throws Exception {
        var result = mvc.get().uri("/ui/reports" + path + "/export").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentType()).contains("text/csv");
        assertThat(result.getResponse().getHeader("Content-Disposition")).contains("attachment");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/revenue", "/products", "/customers", "/inventory",
                             "/orders", "/payments", "/whatsapp"})
    void export_filenameContainsDateRange(String path) throws Exception {
        var result = mvc.get()
                .uri("/ui/reports" + path + "/export?from=2026-07-01&to=2026-07-12")
                .exchange();
        assertThat(result.getResponse().getHeader("Content-Disposition"))
                .contains("2026-07-01").contains("2026-07-12");
    }

    @Test
    void exportRevenue_csvHasHeaderRow() throws Exception {
        assertThat(mvc.get().uri("/ui/reports/revenue/export").exchange()
                .getResponse().getContentAsString())
                .startsWith("Date,Orders,Revenue,Avg Order Value");
    }

    @Test
    void exportProducts_csvHasHeaderRow() throws Exception {
        assertThat(mvc.get().uri("/ui/reports/products/export").exchange()
                .getResponse().getContentAsString())
                .startsWith("SKU,Product,Category");
    }

    @Test
    void exportCustomers_csvHasHeaderRow() throws Exception {
        assertThat(mvc.get().uri("/ui/reports/customers/export").exchange()
                .getResponse().getContentAsString())
                .startsWith("Customer,Phone,Orders,Lifetime Spend");
    }

    @Test
    void exportOrders_csvHasHeaderRow() throws Exception {
        assertThat(mvc.get().uri("/ui/reports/orders/export").exchange()
                .getResponse().getContentAsString())
                .startsWith("Status,Count");
    }

    @Test
    void exportPayments_csvHasHeaderRow() throws Exception {
        assertThat(mvc.get().uri("/ui/reports/payments/export").exchange()
                .getResponse().getContentAsString())
                .startsWith("Customer,Phone,Unpaid Orders");
    }

    @Test
    void exportWhatsapp_csvHasHeaderRow() throws Exception {
        assertThat(mvc.get().uri("/ui/reports/whatsapp/export").exchange()
                .getResponse().getContentAsString())
                .startsWith("Date,Sent,Delivered,Read,Failed,Inbound");
    }
}
