package com.bot.whatsappbotservice.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {

    Optional<PasswordResetCode> findFirstByTenantUserIdOrderByCreatedAtDesc(Long tenantUserId);
}
