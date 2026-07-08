package com.bot.whatsappbotservice.security;

import com.bot.whatsappbotservice.tenant.TenantUser;
import com.bot.whatsappbotservice.tenant.TenantUserRepository;
import com.bot.whatsappbotservice.tenant.UserStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final TenantUserRepository tenantUserRepository;

    public AppUserDetailsService(TenantUserRepository tenantUserRepository) {
        this.tenantUserRepository = tenantUserRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        TenantUser tenantUser = tenantUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("No user found for email " + email));
        Long tenantId = tenantUser.getTenant() != null ? tenantUser.getTenant().getId() : null;
        return new AppUserPrincipal(
                tenantUser.getId(),
                tenantId,
                tenantUser.getEmail(),
                tenantUser.getPasswordHash(),
                tenantUser.getRole(),
                tenantUser.getStatus() == UserStatus.ACTIVE);
    }
}
