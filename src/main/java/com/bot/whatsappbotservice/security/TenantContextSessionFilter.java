package com.bot.whatsappbotservice.security;

import com.bot.whatsappbotservice.common.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The session-based UI counterpart to {@link JwtAuthenticationFilter}'s tenant-context handling:
 * by the time this filter runs, Spring Security has already restored the {@link Authentication}
 * from the HTTP session (if any), so this just reads the tenant id off the already-authenticated
 * {@link AppUserPrincipal} rather than parsing anything itself.
 */
public class TenantContextSessionFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal
                    && principal.getTenantId() != null) {
                TenantContext.setTenantId(principal.getTenantId());
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
