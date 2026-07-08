package com.bot.whatsappbotservice.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    Page<InventoryTransaction> findByInventoryIdOrderByCreatedAtDesc(Long inventoryId, Pageable pageable);
}
