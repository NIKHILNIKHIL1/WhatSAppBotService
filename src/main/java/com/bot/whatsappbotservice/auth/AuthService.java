package com.bot.whatsappbotservice.auth;

import com.bot.whatsappbotservice.auth.dto.AuthResponse;
import com.bot.whatsappbotservice.auth.dto.LoginRequest;
import com.bot.whatsappbotservice.auth.dto.RegisterRequest;
import com.bot.whatsappbotservice.common.exception.BusinessRuleViolationException;
import com.bot.whatsappbotservice.common.exception.DuplicateResourceException;
import com.bot.whatsappbotservice.common.exception.ResourceNotFoundException;
import com.bot.whatsappbotservice.i18n.SupportedLanguage;
import com.bot.whatsappbotservice.security.AppUserPrincipal;
import com.bot.whatsappbotservice.security.JwtService;
import com.bot.whatsappbotservice.security.RefreshTokenService;
import com.bot.whatsappbotservice.tenant.Tenant;
import com.bot.whatsappbotservice.tenant.TenantRepository;
import com.bot.whatsappbotservice.tenant.TenantStatus;
import com.bot.whatsappbotservice.tenant.TenantUser;
import com.bot.whatsappbotservice.tenant.TenantUserRepository;
import com.bot.whatsappbotservice.tenant.UserRole;
import com.bot.whatsappbotservice.tenant.UserStatus;
import java.util.HashSet;
import java.util.Set;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private final TenantRepository tenantRepository;
    private final TenantUserRepository tenantUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(TenantRepository tenantRepository, TenantUserRepository tenantUserRepository,
                        PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager,
                        JwtService jwtService, RefreshTokenService refreshTokenService) {
        this.tenantRepository = tenantRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (tenantRepository.existsBySlug(request.slug())) {
            throw new DuplicateResourceException("A tenant with slug '" + request.slug() + "' already exists");
        }
        if (tenantUserRepository.existsByEmailIgnoreCase(request.adminEmail())) {
            throw new DuplicateResourceException("A user with email '" + request.adminEmail() + "' already exists");
        }

        String defaultLanguageCode = defaultIfBlank(request.defaultLanguageCode(), "en");
        if (!SupportedLanguage.isSupported(defaultLanguageCode)) {
            throw new BusinessRuleViolationException("Unsupported language code: '" + defaultLanguageCode + "'");
        }

        Tenant tenant = new Tenant();
        tenant.setName(request.tenantName());
        tenant.setSlug(request.slug());
        tenant.setCurrencyCode(defaultIfBlank(request.currencyCode(), "INR"));
        tenant.setTimezone(defaultIfBlank(request.timezone(), "UTC"));
        tenant.setDefaultLanguageCode(defaultLanguageCode);
        tenant.setSupportedLanguageCodes(resolveSupportedLanguages(request.supportedLanguageCodes(), defaultLanguageCode));
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant = tenantRepository.save(tenant);

        TenantUser admin = new TenantUser();
        admin.setTenant(tenant);
        admin.setEmail(request.adminEmail().toLowerCase());
        admin.setPasswordHash(passwordEncoder.encode(request.adminPassword()));
        admin.setFullName(request.adminFullName());
        admin.setRole(UserRole.VENDOR_ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin = tenantUserRepository.save(admin);

        return issueTokens(admin);
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email().toLowerCase(), request.password()));
        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        TenantUser tenantUser = tenantUserRepository.findById(principal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return issueTokens(tenantUser);
    }

    @Transactional
    public AuthResponse refresh(String refreshTokenPlaintext) {
        Long tenantUserId = refreshTokenService.validateAndRevoke(refreshTokenPlaintext);
        TenantUser tenantUser = tenantUserRepository.findById(tenantUserId)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BadCredentialsException("User is no longer active"));
        return issueTokens(tenantUser);
    }

    public void logout(String refreshTokenPlaintext) {
        refreshTokenService.revoke(refreshTokenPlaintext);
    }

    private AuthResponse issueTokens(TenantUser tenantUser) {
        Long tenantId = tenantUser.getTenant() != null ? tenantUser.getTenant().getId() : null;
        String accessToken = jwtService.generateAccessToken(
                tenantUser.getId(), tenantId, tenantUser.getRole(), tenantUser.getEmail());
        String refreshToken = refreshTokenService.issue(tenantUser.getId());
        return new AuthResponse(accessToken, refreshToken, "Bearer", jwtService.accessTokenExpirationSeconds());
    }

    private static String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    /**
     * Auto-unions the default language into the requested set rather than rejecting registration
     * outright if a vendor simply forgot to tick their own default — only an unrecognized code is
     * a genuine error.
     */
    private static Set<String> resolveSupportedLanguages(Set<String> requested, String defaultLanguageCode) {
        Set<String> codes = requested != null ? new HashSet<>(requested) : new HashSet<>();
        for (String code : codes) {
            if (!SupportedLanguage.isSupported(code)) {
                throw new BusinessRuleViolationException("Unsupported language code: '" + code + "'");
            }
        }
        codes.add(defaultLanguageCode);
        return codes;
    }
}
