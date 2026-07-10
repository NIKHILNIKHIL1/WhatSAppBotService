package com.bot.whatsappbotservice.security;

import com.bot.whatsappbotservice.tenant.TenantUser;
import com.bot.whatsappbotservice.tenant.TenantUserRepository;
import com.bot.whatsappbotservice.tenant.UserRole;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the single platform SUPER_ADMIN account (no tenant — the platform owner who manages every
 * vendor's settings) from environment variables at startup. There is deliberately no registration
 * path for this role. Idempotent: if the email already has an account, nothing happens — in
 * particular the password is never overwritten from the env var on later startups (rotate it by
 * changing the env var only before the account exists, or via a future change-password flow).
 *
 * <p>Unset credentials merely warn in local/dev, but fail startup in prod/docker: tenant settings
 * are only editable by this role, so a production deployment without it would be unable to
 * configure any vendor.
 */
@Component
public class SuperAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrap.class);
    private static final Set<String> PROFILES_REQUIRING_ADMIN = Set.of("prod", "docker");

    private final TenantUserRepository tenantUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;
    private final String email;
    private final String password;

    public SuperAdminBootstrap(TenantUserRepository tenantUserRepository, PasswordEncoder passwordEncoder,
                                Environment environment,
                                @Value("${app.super-admin.email:}") String email,
                                @Value("${app.super-admin.password:}") String password) {
        this.tenantUserRepository = tenantUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            if (requiresAdmin()) {
                throw new IllegalStateException("app.super-admin.email/password must be configured (set the "
                        + "SUPER_ADMIN_EMAIL and SUPER_ADMIN_PASSWORD environment variables) when running with "
                        + "profile(s) " + String.join(",", environment.getActiveProfiles())
                        + " — tenant settings are unmanageable without a super admin.");
            }
            log.warn("app.super-admin.email/password not set — no SUPER_ADMIN account seeded. Tenant settings "
                    + "cannot be managed until one exists (set SUPER_ADMIN_EMAIL and SUPER_ADMIN_PASSWORD).");
            return;
        }
        if (tenantUserRepository.existsByEmailIgnoreCase(email)) {
            log.debug("Super admin account {} already exists; skipping bootstrap", email);
            return;
        }
        TenantUser admin = new TenantUser();
        admin.setTenant(null);
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setFullName("Platform Admin");
        admin.setRole(UserRole.SUPER_ADMIN);
        tenantUserRepository.save(admin);
        log.info("Seeded SUPER_ADMIN account {}", email);
    }

    private boolean requiresAdmin() {
        for (String profile : environment.getActiveProfiles()) {
            if (PROFILES_REQUIRING_ADMIN.contains(profile)) {
                return true;
            }
        }
        return false;
    }
}
