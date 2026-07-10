package com.bot.whatsappbotservice.tenant;

import com.bot.whatsappbotservice.common.ApiResponse;
import com.bot.whatsappbotservice.tenant.dto.TenantProfileResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only for vendors: settings mutations (WhatsApp/Twilio credentials, provider, languages,
 * ordering policy) are platform-managed and live behind the Super Admin UI — the former vendor
 * update endpoints were removed deliberately, not lost.
 */
@RestController
@RequestMapping("/api/tenants/me")
@PreAuthorize("hasAnyRole('VENDOR_ADMIN', 'VENDOR_STAFF')")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<TenantProfileResponse>> getCurrent() {
        return ResponseEntity.ok(ApiResponse.ok(tenantService.getCurrent()));
    }
}
