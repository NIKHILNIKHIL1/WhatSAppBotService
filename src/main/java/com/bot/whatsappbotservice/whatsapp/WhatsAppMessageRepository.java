package com.bot.whatsappbotservice.whatsapp;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WhatsAppMessageRepository extends JpaRepository<WhatsAppMessage, Long> {

    boolean existsByWaMessageId(String waMessageId);

    /** Daily message counts by delivery status, bucketed by calendar day in the tenant timezone.
     *  Columns: [msg_date, sent, delivered, read_count, failed, inbound]. */
    @Query(value = "SELECT DATE(created_at AT TIME ZONE :tz) AS msg_date, "
            + "COALESCE(SUM(CASE WHEN direction='OUTBOUND' AND status='SENT'      THEN 1 ELSE 0 END), 0) AS sent, "
            + "COALESCE(SUM(CASE WHEN direction='OUTBOUND' AND status='DELIVERED' THEN 1 ELSE 0 END), 0) AS delivered, "
            + "COALESCE(SUM(CASE WHEN direction='OUTBOUND' AND status='READ'      THEN 1 ELSE 0 END), 0) AS read_count, "
            + "COALESCE(SUM(CASE WHEN direction='OUTBOUND' AND status='FAILED'    THEN 1 ELSE 0 END), 0) AS failed, "
            + "COALESCE(SUM(CASE WHEN direction='INBOUND'                         THEN 1 ELSE 0 END), 0) AS inbound "
            + "FROM whatsapp_message "
            + "WHERE tenant_id = :tenantId AND created_at >= :from AND created_at < :to "
            + "GROUP BY msg_date ORDER BY msg_date",
           nativeQuery = true)
    List<Object[]> findDailyEngagement(@Param("tenantId") Long tenantId,
                                        @Param("from") Instant from,
                                        @Param("to") Instant to,
                                        @Param("tz") String tz);
}
