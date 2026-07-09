package com.bot.whatsappbotservice.catalog;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySkuIgnoreCase(String sku);

    Optional<Product> findBySkuIgnoreCase(String sku);

    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);

    Page<Product> findByActiveTrue(Pageable pageable);

    @Query("SELECT DISTINCT p.category.id FROM Product p WHERE p.category.id IN :categoryIds AND p.active = true")
    Set<Long> findCategoryIdsWithActiveProduct(@Param("categoryIds") Collection<Long> categoryIds);
}
