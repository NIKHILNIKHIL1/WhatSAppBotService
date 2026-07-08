package com.bot.whatsappbotservice.catalog;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySkuIgnoreCase(String sku);

    Optional<Product> findBySkuIgnoreCase(String sku);

    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);

    Page<Product> findByActiveTrue(Pageable pageable);
}
