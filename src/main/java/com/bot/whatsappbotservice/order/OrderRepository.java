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
}
