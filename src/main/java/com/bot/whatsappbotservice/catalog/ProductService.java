package com.bot.whatsappbotservice.catalog;

import com.bot.whatsappbotservice.audit.AuditAction;
import com.bot.whatsappbotservice.audit.AuditChannel;
import com.bot.whatsappbotservice.audit.AuditService;
import com.bot.whatsappbotservice.catalog.dto.CreateProductRequest;
import com.bot.whatsappbotservice.catalog.dto.ProductResponse;
import com.bot.whatsappbotservice.catalog.dto.UpdateProductRequest;
import com.bot.whatsappbotservice.common.TenantContext;
import com.bot.whatsappbotservice.common.exception.BusinessRuleViolationException;
import com.bot.whatsappbotservice.common.exception.DuplicateResourceException;
import com.bot.whatsappbotservice.common.exception.ResourceNotFoundException;
import com.bot.whatsappbotservice.i18n.SupportedLanguage;
import com.bot.whatsappbotservice.i18n.Translation;
import com.bot.whatsappbotservice.i18n.TranslationDto;
import com.bot.whatsappbotservice.inventory.Inventory;
import com.bot.whatsappbotservice.inventory.InventoryRepository;
import com.bot.whatsappbotservice.tenant.Tenant;
import com.bot.whatsappbotservice.tenant.TenantRepository;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductMapper productMapper;
    private final AuditService auditService;
    private final TenantRepository tenantRepository;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
                           InventoryRepository inventoryRepository, ProductMapper productMapper,
                           AuditService auditService, TenantRepository tenantRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.inventoryRepository = inventoryRepository;
        this.productMapper = productMapper;
        this.auditService = auditService;
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        String sku = normalizeSku(request.sku());
        if (productRepository.existsBySkuIgnoreCase(sku)) {
            throw new DuplicateResourceException("A product with SKU '" + sku + "' already exists");
        }

        Product product = new Product();
        product.setSku(sku);
        product.setName(request.name());
        product.setDescription(request.description());
        product.setUnit(request.unit());
        product.setPrice(request.price());
        product.setCurrencyCode(resolveTenantCurrency());
        product.setImageUrl(request.imageUrl());
        product.setCategory(resolveCategory(request.categoryId()));
        applyTranslations(product, request.translations());
        product = productRepository.save(product);

        Inventory inventory = new Inventory();
        inventory.setProduct(product);
        inventory.setQuantityOnHand(request.initialQuantity() != null ? request.initialQuantity() : BigDecimal.ZERO);
        inventoryRepository.save(inventory);

        auditService.record("Product", product.getId().toString(), AuditAction.CREATE, null,
                Map.of("sku", product.getSku(), "name", product.getName(), "price", product.getPrice()),
                AuditChannel.API);
        return productMapper.toResponse(product);
    }

    @Transactional
    public ProductResponse update(Long id, UpdateProductRequest request) {
        Product product = getOrThrow(id);
        Map<String, Object> oldSnapshot = Map.of(
                "name", product.getName(), "price", product.getPrice(), "active", product.isActive());

        product.setName(request.name());
        product.setDescription(request.description());
        product.setUnit(request.unit());
        product.setPrice(request.price());
        product.setCurrencyCode(resolveTenantCurrency());
        product.setImageUrl(request.imageUrl());
        product.setActive(request.active());
        product.setCategory(resolveCategory(request.categoryId()));
        applyTranslations(product, request.translations());
        product = productRepository.save(product);

        auditService.record("Product", product.getId().toString(), AuditAction.UPDATE, oldSnapshot,
                Map.of("name", product.getName(), "price", product.getPrice(), "active", product.isActive()),
                AuditChannel.API);
        return productMapper.toResponse(product);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return productMapper.toResponse(getOrThrow(id));
    }

    /** Active products only — the view for anything customer-facing (storefront, order pickers).
     * The category branch must filter active too: deactivated products used to leak into the
     * storefront's category view through here. */
    @Transactional(readOnly = true)
    public Page<ProductResponse> list(Long categoryId, Pageable pageable) {
        Page<Product> page = categoryId != null
                ? productRepository.findByCategoryIdAndActiveTrue(categoryId, pageable)
                : productRepository.findByActiveTrue(pageable);
        return page.map(productMapper::toResponse);
    }

    /** Every product including deactivated ones — the vendor management view. Deactivated
     * products still block their SKU (deactivate() is a soft delete), so hiding them here is what
     * made "already exists" errors unresolvable from the UI. */
    @Transactional(readOnly = true)
    public Page<ProductResponse> listForManagement(Long categoryId, Pageable pageable) {
        Page<Product> page = categoryId != null
                ? productRepository.findByCategoryId(categoryId, pageable)
                : productRepository.findAll(pageable);
        return page.map(productMapper::toResponse);
    }

    @Transactional
    public void deactivate(Long id) {
        Product product = getOrThrow(id);
        product.setActive(false);
        productRepository.save(product);
        auditService.record("Product", id.toString(), AuditAction.DELETE, Map.of("active", true),
                Map.of("active", false), AuditChannel.API);
    }

    @Transactional
    public void reactivate(Long id) {
        Product product = getOrThrow(id);
        product.setActive(true);
        productRepository.save(product);
        auditService.record("Product", id.toString(), AuditAction.UPDATE, Map.of("active", false),
                Map.of("active", true), AuditChannel.API);
    }

    /**
     * Only validated against the fixed 4-language enum, deliberately not against the tenant's
     * *current* supported subset — storing a translation for a language the tenant doesn't yet
     * offer is harmless and forward-compatible if they add it later.
     */
    private void applyTranslations(Product product, Map<String, TranslationDto> translations) {
        product.getTranslations().clear();
        if (translations == null) {
            return;
        }
        translations.forEach((code, dto) -> {
            if (!SupportedLanguage.isSupported(code)) {
                throw new BusinessRuleViolationException("Unsupported language code: '" + code + "'");
            }
            product.getTranslations().put(code, new Translation(dto.name(), dto.description()));
        });
    }

    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> ResourceNotFoundException.of("Category", categoryId));
    }

    private Product getOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Product", id));
    }

    /** Canonical stored form, matched by V11's unique index on (tenant_id, lower(sku)): trimmed
     * and uppercased so 'crm-mlk' and 'CRM-MLK ' can never coexist as distinct products — the
     * bot's SKU quick-order lookup is case-insensitive and assumes exactly one match. */
    private static String normalizeSku(String sku) {
        return sku.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /** Product currency is always the tenant's — a caller-supplied value is deliberately ignored.
     * Orders were already billed in the tenant currency ({@code OrderService.resolveTenantCurrency})
     * regardless of what the product claimed, so a divergent product currency was a display lie
     * waiting to confuse a customer. Same INR fallback idiom as OrderService for non-web contexts. */
    private String resolveTenantCurrency() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return "INR";
        }
        return tenantRepository.findById(tenantId).map(Tenant::getCurrencyCode).orElse("INR");
    }
}
