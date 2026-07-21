package com.bot.whatsappbotservice.report;

import com.bot.whatsappbotservice.customer.CustomerRepository;
import com.bot.whatsappbotservice.inventory.InventoryTransactionRepository;
import com.bot.whatsappbotservice.order.ConcernStatus;
import com.bot.whatsappbotservice.order.OrderConcernRepository;
import com.bot.whatsappbotservice.order.OrderRepository;
import com.bot.whatsappbotservice.order.OrderStatus;
import com.bot.whatsappbotservice.order.PaymentStatus;
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
import com.bot.whatsappbotservice.whatsapp.WhatsAppMessageRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final List<PaymentStatus> UNPAID_STATUSES =
            List.of(PaymentStatus.UNPAID, PaymentStatus.PARTIALLY_PAID);

    private final TenantService tenantService;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final WhatsAppMessageRepository whatsAppMessageRepository;
    private final OrderConcernRepository orderConcernRepository;

    public ReportService(TenantService tenantService,
                         OrderRepository orderRepository,
                         CustomerRepository customerRepository,
                         InventoryTransactionRepository inventoryTransactionRepository,
                         WhatsAppMessageRepository whatsAppMessageRepository,
                         OrderConcernRepository orderConcernRepository) {
        this.tenantService = tenantService;
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
        this.whatsAppMessageRepository = whatsAppMessageRepository;
        this.orderConcernRepository = orderConcernRepository;
    }

    // ─── Date range resolution ────────────────────────────────────────────────

    public DateRange resolveDateRange(LocalDate from, LocalDate to) {
        String tz = tenantService.getCurrent().timezone();
        ZoneId zone = (tz != null && !tz.isBlank()) ? ZoneId.of(tz) : UTC;

        LocalDate today = LocalDate.now(zone);
        LocalDate resolvedFrom = from != null ? from : today.withDayOfMonth(1);
        LocalDate resolvedTo   = to   != null ? to   : today;

        if (resolvedFrom.isAfter(resolvedTo)) {
            log.debug("Report from-date {} is after to-date {}; clamping from to to",
                    resolvedFrom, resolvedTo);
            resolvedFrom = resolvedTo;
        }

        return DateRange.of(resolvedFrom, resolvedTo, zone);
    }

    public String getCurrencyCode() {
        return tenantService.getCurrent().currencyCode();
    }

    // ─── Overview KPIs ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ReportOverview getOverview(DateRange range) {
        BigDecimal rev = orderRepository.sumTotalAmountBetween(
                range.fromInstant(), range.toInstant(), OrderStatus.CANCELLED);
        BigDecimal periodRevenue = coalesce(rev);

        long periodOrders = orderRepository.countOrdersInRange(
                range.fromInstant(), range.toInstant(), OrderStatus.CANCELLED);

        BigDecimal aov = periodOrders > 0
                ? periodRevenue.divide(BigDecimal.valueOf(periodOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal outstanding = orderRepository.sumOutstandingAmount(UNPAID_STATUSES, OrderStatus.CANCELLED);
        BigDecimal outstandingAmount = coalesce(outstanding);
        long outstandingCount = orderRepository.countByPaymentStatusInAndStatusNot(
                UNPAID_STATUSES, OrderStatus.CANCELLED);

        long newCustomers = customerRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                range.fromInstant(), range.toInstant());

        long activeCustomers = orderRepository.countActiveCustomersInRange(
                range.fromInstant(), range.toInstant(), OrderStatus.CANCELLED);

        BigDecimal items = orderRepository.sumItemsSoldInRange(
                range.fromInstant(), range.toInstant(), OrderStatus.CANCELLED);
        BigDecimal itemsSold = coalesce(items);

        long openConcerns = orderConcernRepository.countByStatus(ConcernStatus.OPEN);

        DateRange prev = range.previousPeriod();
        BigDecimal prevRev = orderRepository.sumTotalAmountBetween(
                prev.fromInstant(), prev.toInstant(), OrderStatus.CANCELLED);
        BigDecimal revenueGrowthPercent = null;
        if (prevRev != null && prevRev.compareTo(BigDecimal.ZERO) > 0) {
            revenueGrowthPercent = periodRevenue.subtract(prevRev)
                    .divide(prevRev, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP);
        }

        return new ReportOverview(periodRevenue, periodOrders, aov, outstandingAmount,
                outstandingCount, newCustomers, activeCustomers, itemsSold,
                openConcerns, revenueGrowthPercent);
    }

    // ─── Revenue & Sales ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DailyRevenueSummary> getDailyRevenue(DateRange range) {
        Long tenantId = tenantService.getCurrent().id();
        List<Object[]> rows = orderRepository.findDailyRevenue(
                tenantId, range.fromInstant(), range.toInstant(), range.zone().getId());
        return rows.stream().map(r -> new DailyRevenueSummary(
                toLocalDate(r[0]),
                toLong(r[1]),
                toBigDecimal(r[2])
        )).toList();
    }

    // ─── Product Performance ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProductPerformanceSummary> getProductPerformance(DateRange range) {
        Long tenantId = tenantService.getCurrent().id();
        List<Object[]> rows = orderRepository.findProductPerformance(
                tenantId, range.fromInstant(), range.toInstant());
        return rows.stream().map(r -> new ProductPerformanceSummary(
                ((Number) r[0]).longValue(),
                (String) r[1],
                (String) r[2],
                (String) r[3],
                toBigDecimal(r[4]),
                toBigDecimal(r[5]),
                toLong(r[6]),
                toBigDecimal(r[7])
        )).toList();
    }

    // ─── Customer Analytics ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CustomerSummary> getTopCustomers(DateRange range) {
        Long tenantId = tenantService.getCurrent().id();
        List<Object[]> rows = orderRepository.findTopCustomers(
                tenantId, range.fromInstant(), range.toInstant());
        return rows.stream().map(r -> new CustomerSummary(
                ((Number) r[0]).longValue(),
                (String) r[1],
                (String) r[2],
                toLong(r[3]),
                toBigDecimal(r[4]),
                toInstant(r[5])
        )).toList();
    }

    // ─── Inventory Movements ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<InventoryMovementSummary> getInventoryMovements(DateRange range) {
        Long tenantId = tenantService.getCurrent().id();
        List<Object[]> rows = inventoryTransactionRepository.findDailyMovements(
                tenantId, range.fromInstant(), range.toInstant(), range.zone().getId());
        return rows.stream().map(r -> new InventoryMovementSummary(
                toLocalDate(r[0]),
                toBigDecimal(r[1]),
                toBigDecimal(r[2])
        )).toList();
    }

    // ─── Payment Collection ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PaymentSummary getPaymentSummary(DateRange range) {
        BigDecimal billed = orderRepository.sumTotalAmountBetween(
                range.fromInstant(), range.toInstant(), OrderStatus.CANCELLED);
        BigDecimal collected = orderRepository.sumAmountPaidBetween(
                range.fromInstant(), range.toInstant(), OrderStatus.CANCELLED);
        BigDecimal overdueAmt = orderRepository.sumOutstandingAmount(UNPAID_STATUSES, OrderStatus.CANCELLED);
        long overdueCount = orderRepository.countByPaymentStatusInAndStatusNot(
                UNPAID_STATUSES, OrderStatus.CANCELLED);
        return new PaymentSummary(coalesce(billed), coalesce(collected), overdueCount, coalesce(overdueAmt));
    }

    @Transactional(readOnly = true)
    public List<OutstandingReceivable> getOutstandingReceivables() {
        Long tenantId = tenantService.getCurrent().id();
        List<Object[]> rows = orderRepository.findOutstandingReceivables(tenantId);
        return rows.stream().map(r -> new OutstandingReceivable(
                ((Number) r[0]).longValue(),
                (String) r[1],
                (String) r[2],
                toLong(r[3]),
                toBigDecimal(r[4]),
                toInstant(r[5])
        )).toList();
    }

    // ─── WhatsApp Engagement ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<EngagementSummary> getEngagementSummary(DateRange range) {
        Long tenantId = tenantService.getCurrent().id();
        List<Object[]> rows = whatsAppMessageRepository.findDailyEngagement(
                tenantId, range.fromInstant(), range.toInstant(), range.zone().getId());
        return rows.stream().map(r -> new EngagementSummary(
                toLocalDate(r[0]),
                toLong(r[1]),
                toLong(r[2]),
                toLong(r[3]),
                toLong(r[4]),
                toLong(r[5])
        )).toList();
    }

    // ─── Order Operations ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Long> getOrderStatusCounts(DateRange range) {
        List<Object[]> rows = orderRepository.countByStatusInRange(
                range.fromInstant(), range.toInstant());
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            result.put(row[0].toString(), toLong(row[1]));
        }
        return result;
    }

    // ─── Conversion helpers ───────────────────────────────────────────────────

    private static BigDecimal coalesce(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static LocalDate toLocalDate(Object obj) {
        if (obj instanceof java.sql.Date d) return d.toLocalDate();
        if (obj instanceof LocalDate ld) return ld;
        throw new IllegalArgumentException("Cannot convert to LocalDate: " + obj.getClass());
    }

    private static Instant toInstant(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Instant i) return i;
        if (obj instanceof Timestamp ts) return ts.toInstant();
        if (obj instanceof OffsetDateTime odt) return odt.toInstant();
        throw new IllegalArgumentException("Cannot convert to Instant: " + obj.getClass());
    }

    private static long toLong(Object obj) {
        return obj == null ? 0L : ((Number) obj).longValue();
    }

    private static BigDecimal toBigDecimal(Object obj) {
        if (obj == null) return BigDecimal.ZERO;
        if (obj instanceof BigDecimal bd) return bd;
        return new BigDecimal(obj.toString());
    }
}
