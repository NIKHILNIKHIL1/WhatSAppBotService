package com.bot.whatsappbotservice.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.bot.whatsappbotservice.catalog.Category;
import com.bot.whatsappbotservice.catalog.CategoryRepository;
import com.bot.whatsappbotservice.tenant.Tenant;
import com.bot.whatsappbotservice.tenant.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves the core multi-tenancy safety property: a tenant-scoped entity ({@code Category}, via
 * {@code @TenantId} on {@code TenantScopedEntity}) is invisible to every other tenant purely from
 * Hibernate's automatic filtering, with no explicit tenant_id predicate in any repository method.
 */
@Testcontainers
@SpringBootTest
@Disabled("Disabled because it requires a running PostgreSQL container. Enable when running integration tests.")
class TenantIsolationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void categoriesAreInvisibleAcrossTenants() {
        Tenant tenantA = createTenant("tenant-a-isolation");
        Tenant tenantB = createTenant("tenant-b-isolation");

        TenantContext.setTenantId(tenantA.getId());
        Category categoryA = new Category();
        categoryA.setName("Dairy");
        categoryA = categoryRepository.save(categoryA);
        assertThat(categoryA.getTenantId()).isEqualTo(tenantA.getId());

        TenantContext.setTenantId(tenantB.getId());
        Category categoryB = new Category();
        categoryB.setName("Hardware");
        categoryRepository.save(categoryB);

        // Tenant B must see only its own category, never tenant A's — through findAll()...
        assertThat(categoryRepository.findAll())
                .extracting(Category::getName)
                .containsExactly("Hardware");

        // ...and a direct findById of tenant A's row must come back empty, not just filtered from a list.
        assertThat(categoryRepository.findById(categoryA.getId())).isEmpty();

        TenantContext.setTenantId(tenantA.getId());
        assertThat(categoryRepository.findAll())
                .extracting(Category::getName)
                .containsExactly("Dairy");
    }

    private Tenant createTenant(String slug) {
        Tenant tenant = new Tenant();
        tenant.setName(slug);
        tenant.setSlug(slug);
        return tenantRepository.save(tenant);
    }
}
