package com.bot.whatsappbotservice.tenant;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantUserRepository extends JpaRepository<TenantUser, Long> {

    boolean existsByEmailIgnoreCase(String email);

    Optional<TenantUser> findByEmailIgnoreCase(String email);
}
