package com.bot.whatsappbotservice.inventory;

import com.bot.whatsappbotservice.common.ApiResponse;
import com.bot.whatsappbotservice.inventory.dto.AdjustStockRequest;
import com.bot.whatsappbotservice.inventory.dto.InventoryResponse;
import com.bot.whatsappbotservice.inventory.dto.InventoryTransactionResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<InventoryResponse>> get(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.get(productId)));
    }

    @GetMapping("/{productId}/transactions")
    public ResponseEntity<ApiResponse<Page<InventoryTransactionResponse>>> history(
            @PathVariable Long productId, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.history(productId, pageable)));
    }

    @PostMapping("/{productId}/adjustments")
    @PreAuthorize("hasAnyRole('VENDOR_ADMIN', 'VENDOR_STAFF')")
    public ResponseEntity<ApiResponse<InventoryTransactionResponse>> adjustStock(
            @PathVariable Long productId, @Valid @RequestBody AdjustStockRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.adjustStock(productId, request)));
    }
}
