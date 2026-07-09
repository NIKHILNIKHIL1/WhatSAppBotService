package com.bot.whatsappbotservice.storefront;

import com.bot.whatsappbotservice.common.TenantContext;
import com.bot.whatsappbotservice.tenant.Tenant;
import com.bot.whatsappbotservice.tenant.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Every {@code /store/{tenantSlug}/...} request carries its tenant directly in the URL — unlike
 * the vendor UI/API, there's no JWT claim or session-restored principal to read a tenant id from,
 * since a storefront visitor might not be logged in at all yet (anonymous catalog browsing). This
 * resolves the slug once per request and 404s outright if it doesn't match a tenant, rather than
 * letting the request proceed with {@link TenantContext} unset — the alternative would be every
 * downstream repository call silently running unscoped.
 */
public class StorefrontTenantContextFilter extends OncePerRequestFilter {

    static final String RESOLVED_TENANT_ATTRIBUTE = "storefront.resolvedTenant";

    private final TenantRepository tenantRepository;

    public StorefrontTenantContextFilter(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String slug = extractSlug(request.getRequestURI());
        Optional<Tenant> tenant = slug != null ? tenantRepository.findBySlug(slug) : Optional.empty();
        if (tenant.isEmpty()) {
            response.sendError(HttpStatus.NOT_FOUND.value(), "Store not found");
            return;
        }

        try {
            TenantContext.setTenantId(tenant.get().getId());
            request.setAttribute(RESOLVED_TENANT_ATTRIBUTE, tenant.get());
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    /** {@code /store/{slug}/...} -> {@code slug}, or {@code null} if the path is malformed. */
    private String extractSlug(String requestUri) {
        String[] segments = requestUri.split("/", 4);
        return (segments.length >= 3 && !segments[2].isBlank()) ? segments[2] : null;
    }
}
