package com.bot.whatsappbotservice.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// JpaSpecificationExecutor backs the vendor order list, where status, payment status and date
// range combine freely — eight derived finder methods for the combinations would be noise.
public interface OrderRepository extends JpaRepository<OrderHeader, Long>, JpaSpecificationExecutor<OrderHeader> {

    Optional<OrderHeader> findByIdempotencyKey(String idempotencyKey);

    Page<OrderHeader> findByCustomerId(Long customerId, Pageable pageable);

    List<OrderHeader> findByCustomerIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long customerId, Instant from, Instant to);

    // Dashboard "outstanding" tile: how many orders still owe money and how much in total.
    // Cancelled orders are excluded — nothing is owed on an order that won't be fulfilled.
    long countByPaymentStatusInAndStatusNot(Collection<PaymentStatus> paymentStatuses, OrderStatus excludedStatus);

    /** Returns {@code null} (not zero) when no orders match — callers normalize. */
    @Query("SELECT SUM(o.totalAmount - o.amountPaid) FROM OrderHeader o "
            + "WHERE o.paymentStatus IN :paymentStatuses AND o.status <> :excludedStatus")
    BigDecimal sumOutstandingAmount(@Param("paymentStatuses") Collection<PaymentStatus> paymentStatuses,
                                     @Param("excludedStatus") OrderStatus excludedStatus);

    /** Dashboard period summary: revenue booked in [from, to), cancelled orders excluded.
     * Returns {@code null} (not zero) when no orders match — callers normalize. */
    @Query("SELECT SUM(o.totalAmount) FROM OrderHeader o "
            + "WHERE o.createdAt >= :from AND o.createdAt < :to AND o.status <> :excludedStatus")
    BigDecimal sumTotalAmountBetween(@Param("from") Instant from, @Param("to") Instant to,
                                      @Param("excludedStatus") OrderStatus excludedStatus);

    // ─── Report Module queries ────────────────────────────────────────────────

    @Query("SELECT COUNT(o) FROM OrderHeader o "
            + "WHERE o.createdAt >= :from AND o.createdAt < :to AND o.status <> :excluded")
    long countOrdersInRange(@Param("from") Instant from, @Param("to") Instant to,
                             @Param("excluded") OrderStatus excluded);

    @Query("SELECT COUNT(DISTINCT o.customer.id) FROM OrderHeader o "
            + "WHERE o.createdAt >= :from AND o.createdAt < :to AND o.status <> :excluded")
    long countActiveCustomersInRange(@Param("from") Instant from, @Param("to") Instant to,
                                      @Param("excluded") OrderStatus excluded);

    /** Sums quantity across all OrderItem rows whose parent order falls in [from,to).
     *  Returns 0 (via COALESCE) when no items match — never null. */
    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM OrderItem i "
            + "WHERE i.order.createdAt >= :from AND i.order.createdAt < :to "
            + "AND i.order.status <> :excluded")
    BigDecimal sumItemsSoldInRange(@Param("from") Instant from, @Param("to") Instant to,
                                    @Param("excluded") OrderStatus excluded);

    /** Amount collected (amount_paid) on non-cancelled orders created in [from,to).
     *  Returns {@code null} when no orders match — callers normalize. */
    @Query("SELECT SUM(o.amountPaid) FROM OrderHeader o "
            + "WHERE o.createdAt >= :from AND o.createdAt < :to AND o.status <> :excluded")
    BigDecimal sumAmountPaidBetween(@Param("from") Instant from, @Param("to") Instant to,
                                     @Param("excluded") OrderStatus excluded);

    /** Order count grouped by status — used for the Order Operations report.
     *  Each row is [OrderStatus status, Long count], ordered by count desc. */
    @Query("SELECT o.status, COUNT(o) FROM OrderHeader o "
            + "WHERE o.createdAt >= :from AND o.createdAt < :to "
            + "GROUP BY o.status ORDER BY COUNT(o) DESC")
    List<Object[]> countByStatusInRange(@Param("from") Instant from, @Param("to") Instant to);

    /** Daily revenue trend: [date, orderCount, revenue] grouped by calendar day in the
     *  supplied timezone.  Native query so we can use PostgreSQL's AT TIME ZONE.
     *  tenant_id is passed explicitly because Hibernate's @TenantId filter is not applied
     *  to native queries. */
    @Query(value = "SELECT DATE(created_at AT TIME ZONE :tz) AS rev_date, "
            + "COUNT(id) AS order_count, "
            + "COALESCE(SUM(total_amount), 0) AS revenue "
            + "FROM order_header "
            + "WHERE tenant_id = :tenantId "
            + "  AND created_at >= :from AND created_at < :to "
            + "  AND status <> 'CANCELLED' "
            + "GROUP BY rev_date ORDER BY rev_date",
           nativeQuery = true)
    List<Object[]> findDailyRevenue(@Param("tenantId") Long tenantId,
                                     @Param("from") Instant from,
                                     @Param("to") Instant to,
                                     @Param("tz") String tz);

    /** Top-50 products by revenue in the period.
     *  Columns: [product_id, sku, product_name, category_name,
     *            qty_sold, revenue, order_count, current_stock]. */
    @Query(value = "SELECT oi.product_id, p.sku, p.name, c.name AS category_name, "
            + "COALESCE(SUM(oi.quantity), 0) AS qty_sold, "
            + "COALESCE(SUM(oi.line_total), 0) AS revenue, "
            + "COUNT(DISTINCT oi.order_id) AS order_count, "
            + "(SELECT quantity_on_hand FROM inventory "
            + "  WHERE product_id = oi.product_id AND tenant_id = :tenantId) AS current_stock "
            + "FROM order_item oi "
            + "JOIN order_header oh ON oh.id = oi.order_id "
            + "JOIN product p ON p.id = oi.product_id "
            + "LEFT JOIN category c ON c.id = p.category_id "
            + "WHERE oi.tenant_id = :tenantId "
            + "  AND oh.created_at >= :from AND oh.created_at < :to "
            + "  AND oh.status <> 'CANCELLED' "
            + "GROUP BY oi.product_id, p.sku, p.name, c.name "
            + "ORDER BY revenue DESC LIMIT 50",
           nativeQuery = true)
    List<Object[]> findProductPerformance(@Param("tenantId") Long tenantId,
                                           @Param("from") Instant from,
                                           @Param("to") Instant to);

    /** Top-20 customers by spend in the period.
     *  Columns: [customer_id, full_name, phone_number,
     *            order_count, total_spend, last_order_at]. */
    @Query(value = "SELECT oh.customer_id, c.full_name, c.phone_number, "
            + "COUNT(oh.id) AS order_count, "
            + "COALESCE(SUM(oh.total_amount), 0) AS total_spend, "
            + "MAX(oh.created_at) AS last_order_at "
            + "FROM order_header oh "
            + "JOIN customer c ON c.id = oh.customer_id "
            + "WHERE oh.tenant_id = :tenantId "
            + "  AND oh.created_at >= :from AND oh.created_at < :to "
            + "  AND oh.status <> 'CANCELLED' "
            + "GROUP BY oh.customer_id, c.full_name, c.phone_number "
            + "ORDER BY total_spend DESC LIMIT 20",
           nativeQuery = true)
    List<Object[]> findTopCustomers(@Param("tenantId") Long tenantId,
                                     @Param("from") Instant from,
                                     @Param("to") Instant to);

    /** All customers with outstanding (UNPAID or PARTIALLY_PAID, non-cancelled) orders.
     *  No date filter — reflects current receivables state.
     *  Columns: [customer_id, full_name, phone_number,
     *            unpaid_order_count, outstanding_amount, oldest_unpaid_at]. */
    @Query(value = "SELECT oh.customer_id, c.full_name, c.phone_number, "
            + "COUNT(oh.id) AS unpaid_order_count, "
            + "SUM(oh.total_amount - oh.amount_paid) AS outstanding_amount, "
            + "MIN(oh.created_at) AS oldest_unpaid_at "
            + "FROM order_header oh "
            + "JOIN customer c ON c.id = oh.customer_id "
            + "WHERE oh.tenant_id = :tenantId "
            + "  AND oh.payment_status IN ('UNPAID', 'PARTIALLY_PAID') "
            + "  AND oh.status <> 'CANCELLED' "
            + "GROUP BY oh.customer_id, c.full_name, c.phone_number "
            + "ORDER BY outstanding_amount DESC",
           nativeQuery = true)
    List<Object[]> findOutstandingReceivables(@Param("tenantId") Long tenantId);
}
