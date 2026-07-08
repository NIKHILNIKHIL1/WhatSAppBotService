package com.bot.whatsappbotservice.catalog;

import com.bot.whatsappbotservice.catalog.dto.CategoryResponse;
import com.bot.whatsappbotservice.catalog.dto.CreateCategoryRequest;
import com.bot.whatsappbotservice.catalog.dto.UpdateCategoryRequest;
import com.bot.whatsappbotservice.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    @PreAuthorize("hasRole('VENDOR_ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> create(@Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(categoryService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('VENDOR_ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> update(@PathVariable Long id,
                                                                 @Valid @RequestBody UpdateCategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.update(id, request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.get(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<CategoryResponse>>> list(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.list(pageable)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('VENDOR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        categoryService.deactivate(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
