package com.bot.whatsappbotservice.inventory;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    Page<InventoryTransaction> findByInventoryIdOrderByCreatedAtDesc(Long inventoryId, Pageable pageable);

    /** Daily stock IN / OUT totals, bucketed by calendar day in the tenant timezone.
     *  Positive quantity_delta = IN (receipt/return); negative = OUT (sale/adjustment).
     *  Columns: [txn_date, total_in, total_out]. */
    @Query(value = "SELECT DATE(created_at AT TIME ZONE :tz) AS txn_date, "
            + "COALESCE(SUM(CASE WHEN quantity_delta > 0 THEN quantity_delta ELSE 0 END), 0) AS total_in, "
            + "COALESCE(SUM(CASE WHEN quantity_delta < 0 THEN ABS(quantity_delta) ELSE 0 END), 0) AS total_out "
            + "FROM inventory_transaction "
            + "WHERE tenant_id = :tenantId AND created_at >= :from AND created_at < :to "
            + "GROUP BY txn_date ORDER BY txn_date",
           nativeQuery = true)
    List<Object[]> findDailyMovements(@Param("tenantId") Long tenantId,
                                       @Param("from") Instant from,
                                       @Param("to") Instant to,
                                       @Param("tz") String tz);
}
