package com.bot.whatsappbotservice.security;

import com.bot.whatsappbotservice.common.TenantContext;
import com.bot.whatsappbotservice.tenant.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates requests from a JWT access token, built entirely from the token's own claims
 * (no DB lookup per request). That keeps the hot path stateless, at the cost that a disabled
 * user's already-issued access token stays valid until it expires — mitigated by a short
 * (default 15 min) access-token lifetime; refresh tokens are the actual revocation point.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractBearerToken(request);
            if (token != null) {
                authenticate(token);
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void authenticate(String token) {
        try {
            Claims claims = jwtService.parseClaims(token);
            Long userId = Long.valueOf(claims.getSubject());
            Long tenantId = claims.get("tenantId", Long.class);
            String email = claims.get("email", String.class);
            String role = claims.get("role", String.class);

            var principal = new AppUserPrincipal(userId, tenantId, email, null, UserRole.valueOf(role), true);
            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
            var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            if (tenantId != null) {
                TenantContext.setTenantId(tenantId);
            }
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejecting invalid JWT: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
        }
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
