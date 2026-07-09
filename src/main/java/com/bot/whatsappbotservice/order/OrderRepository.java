package com.bot.whatsappbotservice.order;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderHeader, Long> {

    Optional<OrderHeader> findByIdempotencyKey(String idempotencyKey);

    Page<OrderHeader> findByStatus(OrderStatus status, Pageable pageable);

    Page<OrderHeader> findByCustomerId(Long customerId, Pageable pageable);
}
