package com.bot.whatsappbotservice.inventory;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductId(Long productId);

    // JOIN FETCH on a to-one association is safe to paginate — the well-known Hibernate
    // fetch-join-pagination pitfall (rows fetched into memory to paginate correctly) only applies
    // to to-*many* associations.
    @Query("SELECT i FROM Inventory i JOIN FETCH i.product ORDER BY i.product.name")
    Page<Inventory> findAllWithProduct(Pageable pageable);
}
