package com.bot.whatsappbotservice.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

    Page<OrderStatusHistory> findByOrderIdOrderByCreatedAtDesc(Long orderId, Pageable pageable);
}
