package com.bot.whatsappbotservice.security;

import com.bot.whatsappbotservice.tenant.UserRole;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * {@code Serializable} because the UI's session-based login (unlike the stateless JWT path)
 * stores this principal inside the {@code HttpSession} between requests.
 */
public class AppUserPrincipal implements UserDetails, Serializable {

    private final Long userId;
    private final Long tenantId;
    private final String email;
    private final String passwordHash;
    private final UserRole role;
    private final boolean enabled;

    public AppUserPrincipal(Long userId, Long tenantId, String email, String passwordHash, UserRole role,
                             boolean enabled) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = enabled;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public UserRole getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
