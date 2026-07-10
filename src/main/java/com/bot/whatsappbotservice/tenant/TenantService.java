package com.bot.whatsappbotservice.tenant;

import com.bot.whatsappbotservice.audit.AuditAction;
import com.bot.whatsappbotservice.audit.AuditChannel;
import com.bot.whatsappbotservice.audit.AuditService;
import com.bot.whatsappbotservice.common.TenantContext;
import com.bot.whatsappbotservice.common.exception.BusinessRuleViolationException;
import com.bot.whatsappbotservice.common.exception.DuplicateResourceException;
import com.bot.whatsappbotservice.common.exception.ResourceNotFoundException;
import com.bot.whatsappbotservice.i18n.SupportedLanguage;
import com.bot.whatsappbotservice.tenant.dto.TenantProfileResponse;
import com.bot.whatsappbotservice.tenant.dto.UpdateMessagingProviderRequest;
import com.bot.whatsappbotservice.tenant.dto.UpdateSupportedLanguagesRequest;
import com.bot.whatsappbotservice.tenant.dto.UpdateTwilioConfigRequest;
import com.bot.whatsappbotservice.tenant.dto.UpdateWhatsAppConfigRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Tenant settings are platform-managed: the Super Admin (who operates the SaaS and owns the
 * Meta/Twilio integrations) configures each vendor's messaging credentials, languages and ordering
 * policy. Vendors can read their own profile ({@link #getCurrent()} feeds the vendor UI's language
 * pickers and dashboard) but every mutation is {@code SUPER_ADMIN}-only, enforced here at the
 * service layer so no future controller can accidentally re-expose it. Mutations audit under the
 * <em>target</em> tenant with the admin as {@code performedBy}.
 */
@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final AuditService auditService;

    public TenantService(TenantRepository tenantRepository, AuditService auditService) {
        this.tenantRepository = tenantRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public TenantProfileResponse getCurrent() {
        return toProfileResponse(getCurrentTenantOrThrow());
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public List<TenantProfileResponse> listAll() {
        return tenantRepository.findAll(Sort.by("id")).stream()
                .map(this::toProfileResponse)
                .toList();
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public TenantProfileResponse getById(Long tenantId) {
        return toProfileResponse(getTenantOrThrow(tenantId));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional
    public TenantProfileResponse updateWhatsAppConfig(Long tenantId, UpdateWhatsAppConfigRequest request) {
        Tenant tenant = getTenantOrThrow(tenantId);

        tenantRepository.findByWhatsappPhoneNumberId(request.whatsappPhoneNumberId())
                .filter(other -> !other.getId().equals(tenant.getId()))
                .ifPresent(other -> {
                    throw new DuplicateResourceException(
                            "WhatsApp phone number id '" + request.whatsappPhoneNumberId()
                                    + "' is already linked to another tenant");
                });

        // Deliberately never write the access token itself (old or new) into the audit trail —
        // only that it changed. The audit log is not a secrets store.
        Map<String, Object> oldSnapshot = Map.of(
                "whatsappPhoneNumberId", String.valueOf(tenant.getWhatsappPhoneNumberId()),
                "whatsappBusinessAccountId", String.valueOf(tenant.getWhatsappBusinessAccountId()));

        tenant.setWhatsappPhoneNumberId(request.whatsappPhoneNumberId());
        tenant.setWhatsappBusinessAccountId(request.whatsappBusinessAccountId());
        tenant.setWhatsappAccessToken(request.whatsappAccessToken());
        if (StringUtils.hasText(request.vendorNotificationPhoneNumber())) {
            tenant.setVendorNotificationPhoneNumber(request.vendorNotificationPhoneNumber());
        }
        tenantRepository.save(tenant);

        auditService.recordForTenant(tenant.getId(), "Tenant", tenant.getId().toString(), AuditAction.UPDATE,
                oldSnapshot,
                Map.of("whatsappPhoneNumberId", tenant.getWhatsappPhoneNumberId(),
                        "whatsappBusinessAccountId", String.valueOf(tenant.getWhatsappBusinessAccountId()),
                        "accessTokenChanged", true),
                AuditChannel.API);

        return toProfileResponse(tenant);
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional
    public TenantProfileResponse updateTwilioConfig(Long tenantId, UpdateTwilioConfigRequest request) {
        Tenant tenant = getTenantOrThrow(tenantId);

        tenantRepository.findByTwilioWhatsAppNumber(request.twilioWhatsAppNumber())
                .filter(other -> !other.getId().equals(tenant.getId()))
                .ifPresent(other -> {
                    throw new DuplicateResourceException(
                            "Twilio WhatsApp number '" + request.twilioWhatsAppNumber()
                                    + "' is already linked to another tenant");
                });

        // Deliberately never write the auth token itself (old or new) into the audit trail —
        // only that it changed. The audit log is not a secrets store.
        Map<String, Object> oldSnapshot = Map.of(
                "twilioAccountSid", String.valueOf(tenant.getTwilioAccountSid()),
                "twilioWhatsAppNumber", String.valueOf(tenant.getTwilioWhatsAppNumber()));

        tenant.setTwilioAccountSid(request.twilioAccountSid());
        tenant.setTwilioAuthToken(request.twilioAuthToken());
        tenant.setTwilioWhatsAppNumber(request.twilioWhatsAppNumber());
        tenantRepository.save(tenant);

        auditService.recordForTenant(tenant.getId(), "Tenant", tenant.getId().toString(), AuditAction.UPDATE,
                oldSnapshot,
                Map.of("twilioAccountSid", String.valueOf(tenant.getTwilioAccountSid()),
                        "twilioWhatsAppNumber", String.valueOf(tenant.getTwilioWhatsAppNumber()),
                        "authTokenChanged", true),
                AuditChannel.API);

        return toProfileResponse(tenant);
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional
    public TenantProfileResponse updateSupportedLanguages(Long tenantId, UpdateSupportedLanguagesRequest request) {
        Tenant tenant = getTenantOrThrow(tenantId);

        Set<String> requested = request.supportedLanguageCodes() != null
                ? request.supportedLanguageCodes() : Set.of();
        for (String code : requested) {
            if (!SupportedLanguage.isSupported(code)) {
                throw new BusinessRuleViolationException("Unsupported language code: '" + code + "'");
            }
        }
        // There is currently no way to change the tenant's default language after registration,
        // so the default must always remain in the supported set — otherwise the bot would have
        // no fallback language to use.
        if (!requested.contains(tenant.getDefaultLanguageCode())) {
            throw new BusinessRuleViolationException(
                    "Cannot remove '" + tenant.getDefaultLanguageCode()
                            + "' — it is this store's default language and must stay supported.");
        }

        Set<String> previous = Set.copyOf(tenant.getSupportedLanguageCodes());
        tenant.setSupportedLanguageCodes(new java.util.LinkedHashSet<>(requested));
        tenantRepository.save(tenant);

        auditService.recordForTenant(tenant.getId(), "Tenant", tenant.getId().toString(), AuditAction.UPDATE,
                Map.of("supportedLanguageCodes", previous),
                Map.of("supportedLanguageCodes", tenant.getSupportedLanguageCodes()),
                AuditChannel.API);

        return toProfileResponse(tenant);
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional
    public TenantProfileResponse updateMessagingProvider(Long tenantId, UpdateMessagingProviderRequest request) {
        Tenant tenant = getTenantOrThrow(tenantId);
        MessagingProvider previous = tenant.getMessagingProvider();
        tenant.setMessagingProvider(request.provider());
        tenantRepository.save(tenant);

        auditService.recordForTenant(tenant.getId(), "Tenant", tenant.getId().toString(), AuditAction.UPDATE,
                Map.of("messagingProvider", String.valueOf(previous)),
                Map.of("messagingProvider", String.valueOf(tenant.getMessagingProvider())),
                AuditChannel.API);

        return toProfileResponse(tenant);
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional
    public TenantProfileResponse updateCustomerRegistrationPolicy(Long tenantId, boolean requireCustomerRegistration) {
        Tenant tenant = getTenantOrThrow(tenantId);
        boolean previous = tenant.isRequireCustomerRegistration();
        tenant.setRequireCustomerRegistration(requireCustomerRegistration);
        tenantRepository.save(tenant);

        auditService.recordForTenant(tenant.getId(), "Tenant", tenant.getId().toString(), AuditAction.UPDATE,
                Map.of("requireCustomerRegistration", previous),
                Map.of("requireCustomerRegistration", tenant.isRequireCustomerRegistration()),
                AuditChannel.API);

        return toProfileResponse(tenant);
    }

    private Tenant getCurrentTenantOrThrow() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new ResourceNotFoundException("No tenant context for this request");
        }
        return getTenantOrThrow(tenantId);
    }

    private Tenant getTenantOrThrow(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> ResourceNotFoundException.of("Tenant", tenantId));
    }

    private TenantProfileResponse toProfileResponse(Tenant tenant) {
        return new TenantProfileResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getWhatsappPhoneNumberId(),
                tenant.getWhatsappBusinessAccountId(),
                StringUtils.hasText(tenant.getWhatsappAccessToken()),
                tenant.getVendorNotificationPhoneNumber(),
                tenant.getDefaultLanguageCode(),
                tenant.getCurrencyCode(),
                tenant.getTimezone(),
                tenant.getStatus().name(),
                tenant.getMessagingProvider().name(),
                tenant.getTwilioWhatsAppNumber(),
                StringUtils.hasText(tenant.getTwilioAuthToken()),
                SupportedLanguage.orderedCodes(tenant.getSupportedLanguageCodes()),
                tenant.isRequireCustomerRegistration());
    }
}
