package com.bot.whatsappbotservice.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bot.whatsappbotservice.audit.AuditService;
import com.bot.whatsappbotservice.catalog.dto.CreateProductRequest;
import com.bot.whatsappbotservice.common.TenantContext;
import com.bot.whatsappbotservice.common.exception.DuplicateResourceException;
import com.bot.whatsappbotservice.inventory.InventoryRepository;
import com.bot.whatsappbotservice.tenant.Tenant;
import com.bot.whatsappbotservice.tenant.TenantRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private AuditService auditService;
    @Mock
    private TenantRepository tenantRepository;

    private ProductService productService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        productService = new ProductService(productRepository, categoryRepository, inventoryRepository,
                productMapper, auditService, tenantRepository);
        when(productRepository.save(any())).thenAnswer(inv -> {
            Product product = inv.getArgument(0);
            product.setId(42L);
            return product;
        });
        when(inventoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createTrimsAndUppercasesSkuBeforeCheckingAndSaving() {
        when(productRepository.existsBySkuIgnoreCase("CRM-MLK")).thenReturn(false);

        productService.create(request("  crm-mlk "));

        verify(productRepository).existsBySkuIgnoreCase("CRM-MLK");
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getSku()).isEqualTo("CRM-MLK");
    }

    @Test
    void createRejectsDuplicateSkuDifferingOnlyInCaseOrWhitespace() {
        when(productRepository.existsBySkuIgnoreCase("CRM-MLK")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(request("crm-mlk ")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("CRM-MLK");
        verify(productRepository, never()).save(any());
    }

    @Test
    void createUsesTenantCurrencyAndIgnoresCallerSuppliedCurrency() {
        TenantContext.setTenantId(1L);
        Tenant tenant = new Tenant();
        tenant.setCurrencyCode("USD");
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(productRepository.existsBySkuIgnoreCase("CRM-MLK")).thenReturn(false);

        productService.create(new CreateProductRequest("CRM-MLK", "Cream Milk", null, null, "ltr",
                new BigDecimal("60.00"), "EUR", null, null, null));

        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getCurrencyCode()).isEqualTo("USD");
    }

    @Test
    void createFallsBackToInrWithoutTenantContext() {
        when(productRepository.existsBySkuIgnoreCase("CRM-MLK")).thenReturn(false);

        productService.create(request("CRM-MLK"));

        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getCurrencyCode()).isEqualTo("INR");
    }

    private static CreateProductRequest request(String sku) {
        return new CreateProductRequest(sku, "Cream Milk", null, null, "ltr",
                new BigDecimal("60.00"), null, null, null, null);
    }
}
