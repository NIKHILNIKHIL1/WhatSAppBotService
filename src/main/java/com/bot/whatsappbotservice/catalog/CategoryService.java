package com.bot.whatsappbotservice.catalog;

import com.bot.whatsappbotservice.audit.AuditAction;
import com.bot.whatsappbotservice.audit.AuditChannel;
import com.bot.whatsappbotservice.audit.AuditService;
import com.bot.whatsappbotservice.catalog.dto.CategoryResponse;
import com.bot.whatsappbotservice.catalog.dto.CreateCategoryRequest;
import com.bot.whatsappbotservice.catalog.dto.UpdateCategoryRequest;
import com.bot.whatsappbotservice.common.exception.BusinessRuleViolationException;
import com.bot.whatsappbotservice.common.exception.ResourceNotFoundException;
import com.bot.whatsappbotservice.i18n.SupportedLanguage;
import com.bot.whatsappbotservice.i18n.Translation;
import com.bot.whatsappbotservice.i18n.TranslationDto;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final AuditService auditService;

    public CategoryService(CategoryRepository categoryRepository, CategoryMapper categoryMapper,
                            AuditService auditService) {
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
        this.auditService = auditService;
    }

    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        Category category = new Category();
        category.setName(request.name());
        category.setDescription(request.description());
        category.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : 0);
        category.setParentCategory(resolveParent(request.parentCategoryId()));
        applyTranslations(category, request.translations());
        category = categoryRepository.save(category);

        auditService.record("Category", category.getId().toString(), AuditAction.CREATE, null,
                Map.of("name", category.getName(), "active", category.isActive()), AuditChannel.API);
        return categoryMapper.toResponse(category);
    }

    @Transactional
    public CategoryResponse update(Long id, UpdateCategoryRequest request) {
        Category category = getOrThrow(id);
        Map<String, Object> oldSnapshot = Map.of(
                "name", category.getName(), "displayOrder", category.getDisplayOrder(), "active", category.isActive());

        category.setName(request.name());
        category.setDescription(request.description());
        category.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : 0);
        category.setActive(request.active());
        category.setParentCategory(resolveParent(request.parentCategoryId()));
        applyTranslations(category, request.translations());
        category = categoryRepository.save(category);

        auditService.record("Category", category.getId().toString(), AuditAction.UPDATE, oldSnapshot,
                Map.of("name", category.getName(), "displayOrder", category.getDisplayOrder(),
                        "active", category.isActive()),
                AuditChannel.API);
        return categoryMapper.toResponse(category);
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(Long id) {
        return categoryMapper.toResponse(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Page<CategoryResponse> list(Pageable pageable) {
        return categoryRepository.findByActiveTrue(pageable).map(categoryMapper::toResponse);
    }

    @Transactional
    public void deactivate(Long id) {
        Category category = getOrThrow(id);
        category.setActive(false);
        categoryRepository.save(category);
        auditService.record("Category", id.toString(), AuditAction.DELETE, Map.of("active", true),
                Map.of("active", false), AuditChannel.API);
    }

    /**
     * Only validated against the fixed 4-language enum, deliberately not against the tenant's
     * *current* supported subset — storing a translation for a language the tenant doesn't yet
     * offer is harmless and forward-compatible if they add it later.
     */
    private void applyTranslations(Category category, Map<String, TranslationDto> translations) {
        category.getTranslations().clear();
        if (translations == null) {
            return;
        }
        translations.forEach((code, dto) -> {
            if (!SupportedLanguage.isSupported(code)) {
                throw new BusinessRuleViolationException("Unsupported language code: '" + code + "'");
            }
            category.getTranslations().put(code, new Translation(dto.name(), dto.description()));
        });
    }

    private Category resolveParent(Long parentCategoryId) {
        if (parentCategoryId == null) {
            return null;
        }
        return getOrThrow(parentCategoryId);
    }

    private Category getOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Category", id));
    }
}
