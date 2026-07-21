package com.bot.whatsappbotservice.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.bot.whatsappbotservice.customer.CustomerRepository;
import com.bot.whatsappbotservice.inventory.InventoryTransactionRepository;
import com.bot.whatsappbotservice.order.ConcernStatus;
import com.bot.whatsappbotservice.order.OrderConcernRepository;
import com.bot.whatsappbotservice.order.OrderRepository;
import com.bot.whatsappbotservice.order.OrderStatus;
import com.bot.whatsappbotservice.report.dto.CustomerSummary;
import com.bot.whatsappbotservice.report.dto.DailyRevenueSummary;
import com.bot.whatsappbotservice.report.dto.DateRange;
import com.bot.whatsappbotservice.report.dto.EngagementSummary;
import com.bot.whatsappbotservice.report.dto.InventoryMovementSummary;
import com.bot.whatsappbotservice.report.dto.OutstandingReceivable;
import com.bot.whatsappbotservice.report.dto.PaymentSummary;
import com.bot.whatsappbotservice.report.dto.ProductPerformanceSummary;
import com.bot.whatsappbotservice.report.dto.ReportOverview;
import com.bot.whatsappbotservice.tenant.TenantService;
import com.bot.whatsappbotservice.tenant.dto.TenantProfileResponse;
import com.bot.whatsappbotservice.whatsapp.WhatsAppMessageRepository;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ReportServiceTest {

    @Mock private TenantService tenantService;
    @Mock private OrderRepository orderRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private InventoryTransactionRepository inventoryTransactionRepository;
    @Mock private WhatsAppMessageRepository whatsAppMessageRepository;
    @Mock private OrderConcernRepository orderConcernRepository;

    private ReportService reportService;

    private static final LocalDate JUL_1  = LocalDate.of(2026, 7, 1);
    private static final LocalDate JUL_10 = LocalDate.of(2026, 7, 10);
    private static final DateRange JULY   = DateRange.of(JUL_1, JUL_10, ZoneId.of("UTC"));

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        reportService = new ReportService(tenantService, orderRepository, customerRepository,
                inventoryTransactionRepository, whatsAppMessageRepository, orderConcernRepository);
    }

    private static List<Object[]> wrap(Object[] row) {
        List<Object[]> list = new java.util.ArrayList<>(1);
        list.add(row);
        return list;
    }

    private TenantProfileResponse profile(String timezone) {
        return new TenantProfileResponse(1L, "Test Tenant", "test", null, null, false, null,
                "en", "INR", timezone, "ACTIVE", "META", null, false, List.of("en"), true);
    }

    // ─── resolveDateRange (unchanged tests) ──────────────────────────────────

    @Test
    void resolveDateRange_withExplicitDates_returnsThoseDates() {
        when(tenantService.getCurrent()).thenReturn(profile("UTC"));
        DateRange range = reportService.resolveDateRange(JUL_1, JUL_10);
        assertThat(range.from()).isEqualTo(JUL_1);
        assertThat(range.to()).isEqualTo(JUL_10);
    }

    @Test
    void resolveDateRange_convertsToInstantsInTenantTimezone() {
        when(tenantService.getCurrent()).thenReturn(profile("Asia/Kolkata"));
        ZoneId ist = ZoneId.of("Asia/Kolkata");
        DateRange range = reportService.resolveDateRange(JUL_1, JUL_10);
        assertThat(range.fromInstant()).isEqualTo(JUL_1.atStartOfDay(ist).toInstant());
        assertThat(range.toInstant()).isEqualTo(JUL_10.plusDays(1).atStartOfDay(ist).toInstant());
    }

    @Test
    void resolveDateRange_withNullFrom_defaultsToFirstDayOfCurrentMonth() {
        when(tenantService.getCurrent()).thenReturn(profile("UTC"));
        DateRange range = reportService.resolveDateRange(null, JUL_10);
        assertThat(range.from()).isEqualTo(LocalDate.of(2026, 7, 1));
    }

    @Test
    void resolveDateRange_withNullTo_defaultsToTodayInTenantZone() {
        when(tenantService.getCurrent()).thenReturn(profile("UTC"));
        DateRange range = reportService.resolveDateRange(JUL_1, null);
        assertThat(range.to()).isEqualTo(LocalDate.now(ZoneId.of("UTC")));
    }

    @Test
    void resolveDateRange_whenFromIsAfterTo_clampsFromToTo() {
        when(tenantService.getCurrent()).thenReturn(profile("UTC"));
        DateRange range = reportService.resolveDateRange(JUL_10, JUL_1);
        assertThat(range.from()).isEqualTo(JUL_1);
        assertThat(range.to()).isEqualTo(JUL_1);
    }

    @Test
    void resolveDateRange_withNullTimezone_fallsBackToUtc() {
        when(tenantService.getCurrent()).thenReturn(profile(null));
        DateRange range = reportService.resolveDateRange(JUL_1, JUL_10);
        assertThat(range.zone()).isEqualTo(ZoneId.of("UTC"));
    }

    @Test
    void resolveDateRange_withBlankTimezone_fallsBackToUtc() {
        when(tenantService.getCurrent()).thenReturn(profile("  "));
        DateRange range = reportService.resolveDateRange(JUL_1, JUL_10);
        assertThat(range.zone()).isEqualTo(ZoneId.of("UTC"));
    }

    @Test
    void resolveDateRange_daysSpanIsCorrect() {
        when(tenantService.getCurrent()).thenReturn(profile("UTC"));
        DateRange range = reportService.resolveDateRange(JUL_1, JUL_10);
        assertThat(range.days()).isEqualTo(10);
    }

    @Test
    void resolveDateRange_previousPeriodHasSameLengthAndAbuts() {
        when(tenantService.getCurrent()).thenReturn(profile("UTC"));
        DateRange range = reportService.resolveDateRange(JUL_1, JUL_10);
        DateRange prev = range.previousPeriod();
        assertThat(prev.days()).isEqualTo(range.days());
        assertThat(prev.to()).isEqualTo(JUL_1.minusDays(1));
    }

    // ─── getCurrencyCode ─────────────────────────────────────────────────────

    @Test
    void getCurrencyCode_returnsTenantCurrency() {
        when(tenantService.getCurrent()).thenReturn(profile("UTC"));
        assertThat(reportService.getCurrencyCode()).isEqualTo("INR");
    }

    // ─── getOverview ─────────────────────────────────────────────────────────

    @Test
    void getOverview_aggregatesKpisCorrectly() {
        when(tenantService.getCurrent()).thenReturn(profile("UTC"));
        when(orderRepository.sumTotalAmountBetween(any(), any(), eq(OrderStatus.CANCELLED)))
                .thenReturn(new BigDecimal("50000"));
        when(orderRepository.countOrdersInRange(any(), any(), eq(OrderStatus.CANCELLED)))
                .thenReturn(20L);
        when(orderRepository.sumOutstandingAmount(any(), eq(OrderStatus.CANCELLED)))
                .thenReturn(new BigDecimal("8000"));
        when(orderRepository.countByPaymentStatusInAndStatusNot(any(), eq(OrderStatus.CANCELLED)))
                .thenReturn(5L);
        when(customerRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any()))
                .thenReturn(3L);
        when(orderRepository.countActiveCustomersInRange(any(), any(), eq(OrderStatus.CANCELLED)))
                .thenReturn(12L);
        when(orderRepository.sumItemsSoldInRange(any(), any(), eq(OrderStatus.CANCELLED)))
                .thenReturn(new BigDecimal("150"));
        when(orderConcernRepository.countByStatus(ConcernStatus.OPEN)).thenReturn(2L);

        ReportOverview overview = reportService.getOverview(JULY);

        assertThat(overview.periodRevenue()).isEqualByComparingTo("50000");
        assertThat(overview.periodOrders()).isEqualTo(20L);
        assertThat(overview.averageOrderValue()).isEqualByComparingTo("2500.00");
        assertThat(overview.outstandingAmount()).isEqualByComparingTo("8000");
        assertThat(overview.outstandingOrderCount()).isEqualTo(5L);
        assertThat(overview.newCustomers()).isEqualTo(3L);
        assertThat(overview.activeCustomers()).isEqualTo(12L);
        assertThat(overview.itemsSold()).isEqualByComparingTo("150");
        assertThat(overview.openConcerns()).isEqualTo(2L);
    }

    @Test
    void getOverview_nullRevenueNormalisedToZero() {
        when(tenantService.getCurrent()).thenReturn(profile("UTC"));
        when(orderRepository.sumTotalAmountBetween(any(), any(), any())).thenReturn(null);
        when(orderRepository.countOrdersInRange(any(), any(), any())).thenReturn(0L);
        when(orderRepository.sumOutstandingAmount(any(), any())).thenReturn(null);
        when(orderRepository.countByPaymentStatusInAndStatusNot(any(), any())).thenReturn(0L);
        when(customerRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(0L);
        when(orderRepository.countActiveCustomersInRange(any(), any(), any())).thenReturn(0L);
        when(orderRepository.sumItemsSoldInRange(any(), any(), any())).thenReturn(null);
        when(orderConcernRepository.countByStatus(any())).thenReturn(0L);

        ReportOverview overview = reportService.getOverview(JULY);

        assertThat(overview.periodRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(overview.averageOrderValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(overview.itemsSold()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(overview.revenueGrowthPercent()).isNull();
    }

    @Test
    void getOverview_computesRevenueGrowthPercent() {
        when(tenantService.getCurrent()).thenReturn(profile("UTC"));
        // Current period: 12000, previous period: 10000 → +20%
        when(orderRepository.sumTotalAmountBetween(eq(JULY.fromInstant()), eq(JULY.toInstant()), any()))
                .thenReturn(new BigDecimal("12000"));
        when(orderRepository.sumTotalAmountBetween(
                eq(JULY.previousPeriod().fromInstant()), eq(JULY.previousPeriod().toInstant()), any()))
                .thenReturn(new BigDecimal("10000"));
        when(orderRepository.countOrdersInRange(any(), any(), any())).thenReturn(5L);
        when(orderRepository.sumOutstandingAmount(any(), any())).thenReturn(null);
        when(orderRepository.countByPaymentStatusInAndStatusNot(any(), any())).thenReturn(0L);
        when(customerRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(0L);
        when(orderRepository.countActiveCustomersInRange(any(), any(), any())).thenReturn(0L);
        when(orderRepository.sumItemsSoldInRange(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(orderConcernRepository.countByStatus(any())).thenReturn(0L);

        ReportOverview overview = reportService.getOverview(JULY);

        assertThat(overview.revenueGrowthPercent()).isEqualByComparingTo("20.0");
    }

    // ─── getDailyRevenue ─────────────────────────────────────────────────────

    @Test
    void getDailyRevenue_mapsObjectArraysToDtos() {
        when(tenantService.getCurrent()).thenReturn(profile("UTC"));
        Object[] row = {Date.valueOf(JUL_1), 3L, new BigDecimal("9000")};
        when(orderRepository.findDailyRevenue(eq(1L), any(), any(), eq("UTC")))
                .thenReturn(wrap(row));

        List<DailyRevenueSummary> result = reportService.getDailyRevenue(JULY);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).date()).isEqualTo(JUL_1);
        assertThat(result.get(0).orderCount()).isEqualTo(3L);
        assertThat(result.get(0).revenue()).isEqualByComparingTo("9000");
    }

    @Test
    void getDailyRevenue_emptyWhenNoOrders() {
        when(tenantService.getCurrent()).thenReturn(profile("UTC"));
        when(orderRepository.findDailyRevenue(any(), any(), any(), any())).thenReturn(List.of());
        assertThat(reportService.getDailyRevenue(JULY)).isEmpty();
    }

    // ─── getProductPerformance ────────────────────────────────────────────────

    @Test
    void getProductPerformance_mapsObjectArraysToDtos() {
        when(tenantService.getCurrent()).thenReturn(profile("UTC"));
        Object[] row = {42L, "SKU-01", "Widget", "Gadgets",
                new BigDecimal("100"), new BigDecimal("5000"), 10L, new BigDecimal("25")};
        when(orderRepository.findProductPerformance(eq(1L), any(), any())).thenReturn(wrap(row));

        List<ProductPerformanceSummary> result = reportService.getProductPerformance(JULY);

        assertThat(result).hasSize(1);
        ProductPerformanceSummary p = result.get(0);
        assertThat(p.productId()).isEqualTo(42L);
        assertThat(p.sku()).isEqualTo("SKU-01");
        assertThat(p.productName()).isEqualTo("Widget");
        assertThat(p.categoryName()).isEqualTo("Gadgets");
        assertThat(p.quantitySold()).isEqualByComparingTo("100");
        assertThat(p.revenue()).isEqualByComparingTo("5000");
        assertThat(p.orderCount()).isEqualTo(10L);
        assertThat(p.currentStock()).isEqualByComparingTo("25");
    }

    // ─── getTopCustomers ─────────────────────────────────────────────────────

    @Test
    void getTopCustomers_mapsObjectArraysToDtos() {
        when(tenantService.getCurrent()).thenReturn(profile("UTC"));
        Instant now = Instant.now();
        Object[] row = {7L, "Alice", "+911234567890", 5L, new BigDecimal("15000"),
                new Timestamp(now.toEpochMilli())};
        when(orderRepository.findTopCustomers(eq(1L), any(), any())).thenReturn(wrap(row));

        List<CustomerSummary> result = reportService.getTopCustomers(JULY);

        assertThat(result).hasSize(1);
        CustomerSummary c = result.get(0);
        assertThat(c.customerId()).isEqualTo(7L);
        assertThat(c.fullName()).isEqualTo("Alice");
        assertThat(c.lifetimeOrders()).isEqualTo(5L);
        assertThat(c.lifetimeSpend()).isEqualByComparingTo("15000");
    }

    // ─── getInventoryMovements ────────────────────────────────────────────────

    @Test
    void getInventoryMovements_mapsObjectArraysToDtos() {
        when(tenantService.getCurrent()).thenReturn(profile("UTC"));
        Object[] row = {Date.valueOf(JUL_1), new BigDecimal("200"), new BigDecimal("50")};
        when(inventoryTransactionRepository.findDailyMovements(eq(1L), any(), any(), eq("UTC")))
                .thenReturn(wrap(row));

        List<InventoryMovementSummary> result = reportService.getInventoryMovements(JULY);

        assertThat(result).hasSize(1);
        InventoryMovementSummary m = result.get(0);
        assertThat(m.date()).isEqualTo(JUL_1);
        assertThat(m.totalIn()).isEqualByComparingTo("200");
        assertThat(m.totalOut()).isEqualByComparingTo("50");
        assertThat(m.netChange()).isEqualByComparingTo("150");
    }

    // ─── getPaymentSummary ────────────────────────────────────────────────────

    @Test
    void getPaymentSummary_buildsSummaryCorrectly() {
        when(tenantService.getCurrent()).thenReturn(profile("UTC"));
        when(orderRepository.sumTotalAmountBetween(any(), any(), eq(OrderStatus.CANCELLED)))
                .thenReturn(new BigDecimal("30000"));
        when(orderRepository.sumAmountPaidBetween(any(), any(), eq(OrderStatus.CANCELLED)))
                .thenReturn(new BigDecimal("22000"));
        when(orderRepository.sumOutstandingAmount(any(), eq(OrderStatus.CANCELLED)))
                .thenReturn(new BigDecimal("12000"));
        when(orderRepository.countByPaymentStatusInAndStatusNot(any(), eq(OrderStatus.CANCELLED)))
                .thenReturn(8L);

        PaymentSummary summary = reportService.getPaymentSummary(JULY);

        assertThat(summary.totalBilled()).isEqualByComparingTo("30000");
        assertThat(summary.totalCollected()).isEqualByComparingTo("22000");
        assertThat(summary.overdueAmount()).isEqualByComparingTo("12000");
        assertThat(summary.overdueCount()).isEqualTo(8L);
        assertThat(summary.collectionRate()).isEqualByComparingTo("73.3");
    }

    // ─── getOutstandingReceivables ────────────────────────────────────────────

    @Test
    void getOutstandingReceivables_mapsObjectArraysToDtos() {
        when(tenantService.getCurrent()).thenReturn(profile("UTC"));
        Instant oldest = Instant.parse("2026-06-01T10:00:00Z");
        Object[] row = {9L, "Bob", "+919876543210", 3L, new BigDecimal("4500"),
                new Timestamp(oldest.toEpochMilli())};
        when(orderRepository.findOutstandingReceivables(eq(1L))).thenReturn(wrap(row));

        List<OutstandingReceivable> result = reportService.getOutstandingReceivables();

        assertThat(result).hasSize(1);
        OutstandingReceivable r = result.get(0);
        assertThat(r.customerId()).isEqualTo(9L);
        assertThat(r.customerName()).isEqualTo("Bob");
        assertThat(r.unpaidOrderCount()).isEqualTo(3L);
        assertThat(r.outstandingAmount()).isEqualByComparingTo("4500");
        assertThat(r.oldestUnpaidOrderAt()).isEqualTo(oldest);
    }

    // ─── getEngagementSummary ─────────────────────────────────────────────────

    @Test
    void getEngagementSummary_mapsObjectArraysToDtos() {
        when(tenantService.getCurrent()).thenReturn(profile("UTC"));
        Object[] row = {Date.valueOf(JUL_1), 100L, 90L, 70L, 5L, 30L};
        when(whatsAppMessageRepository.findDailyEngagement(eq(1L), any(), any(), eq("UTC")))
                .thenReturn(wrap(row));

        List<EngagementSummary> result = reportService.getEngagementSummary(JULY);

        assertThat(result).hasSize(1);
        EngagementSummary e = result.get(0);
        assertThat(e.date()).isEqualTo(JUL_1);
        assertThat(e.sent()).isEqualTo(100L);
        assertThat(e.delivered()).isEqualTo(90L);
        assertThat(e.read()).isEqualTo(70L);
        assertThat(e.failed()).isEqualTo(5L);
        assertThat(e.inbound()).isEqualTo(30L);
        assertThat(e.deliveryRate()).isEqualTo(90.0);
        assertThat(e.readRate()).isEqualTo(70.0);
    }

    // ─── getOrderStatusCounts ─────────────────────────────────────────────────

    @Test
    void getOrderStatusCounts_returnsMapKeyedByStatusName() {
        when(tenantService.getCurrent()).thenReturn(profile("UTC"));
        when(orderRepository.countByStatusInRange(any(), any()))
                .thenReturn(List.of(new Object[]{"DELIVERED", 15L}, new Object[]{"NEW", 3L}));

        Map<String, Long> result = reportService.getOrderStatusCounts(JULY);

        assertThat(result).containsEntry("DELIVERED", 15L).containsEntry("NEW", 3L);
    }
}
