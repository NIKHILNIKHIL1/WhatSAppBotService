package com.bot.whatsappbotservice.inventory;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductId(Long productId);

    /** SELECT ... FOR UPDATE — serializes concurrent stock writers on the row itself, so an order
     * deduction racing another deduction (or a manual adjustment) waits instead of failing on the
     * {@code @Version} check. Requires an active transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId")
    Optional<Inventory> findByProductIdForUpdate(@Param("productId") Long productId);

    // JOIN FETCH on a to-one association is safe to paginate — the well-known Hibernate
    // fetch-join-pagination pitfall (rows fetched into memory to paginate correctly) only applies
    // to to-*many* associations.
    @Query("SELECT i FROM Inventory i JOIN FETCH i.product ORDER BY i.product.name")
    Page<Inventory> findAllWithProduct(Pageable pageable);

    List<Inventory> findByProductIdIn(Collection<Long> productIds);

    @Query("SELECT DISTINCT p.category.id FROM Inventory i JOIN i.product p "
            + "WHERE p.category.id IN :categoryIds AND p.active = true AND i.quantityOnHand > 0")
    Set<Long> findCategoryIdsWithInStockProduct(@Param("categoryIds") Collection<Long> categoryIds);
}
