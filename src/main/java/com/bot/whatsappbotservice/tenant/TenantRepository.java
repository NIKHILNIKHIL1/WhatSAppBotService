package com.bot.whatsappbotservice.tenant;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    boolean existsBySlug(String slug);

    Optional<Tenant> findBySlug(String slug);

    Optional<Tenant> findByWhatsappPhoneNumberId(String whatsappPhoneNumberId);

    Optional<Tenant> findByTwilioWhatsAppNumber(String twilioWhatsAppNumber);
}
