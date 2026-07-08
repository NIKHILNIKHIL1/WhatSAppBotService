package com.bot.whatsappbotservice.tenant;

import com.bot.whatsappbotservice.common.ApiResponse;
import com.bot.whatsappbotservice.tenant.dto.TenantProfileResponse;
import com.bot.whatsappbotservice.tenant.dto.UpdateMessagingProviderRequest;
import com.bot.whatsappbotservice.tenant.dto.UpdateTwilioConfigRequest;
import com.bot.whatsappbotservice.tenant.dto.UpdateWhatsAppConfigRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @PutMapping("/whatsapp-config")
    @PreAuthorize("hasRole('VENDOR_ADMIN')")
    public ResponseEntity<ApiResponse<TenantProfileResponse>> updateWhatsAppConfig(
            @Valid @RequestBody UpdateWhatsAppConfigRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(tenantService.updateWhatsAppConfig(request)));
    }

    @PutMapping("/twilio-config")
    @PreAuthorize("hasRole('VENDOR_ADMIN')")
    public ResponseEntity<ApiResponse<TenantProfileResponse>> updateTwilioConfig(
            @Valid @RequestBody UpdateTwilioConfigRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(tenantService.updateTwilioConfig(request)));
    }

    @PutMapping("/messaging-provider")
    @PreAuthorize("hasRole('VENDOR_ADMIN')")
    public ResponseEntity<ApiResponse<TenantProfileResponse>> updateMessagingProvider(
            @Valid @RequestBody UpdateMessagingProviderRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(tenantService.updateMessagingProvider(request)));
    }
}
