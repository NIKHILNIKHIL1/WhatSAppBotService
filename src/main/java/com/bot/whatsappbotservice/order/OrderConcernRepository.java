package com.bot.whatsappbotservice.order;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderConcernRepository extends JpaRepository<OrderConcern, Long> {

    List<OrderConcern> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    long countByStatus(ConcernStatus status);
}
