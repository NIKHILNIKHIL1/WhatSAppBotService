package com.bot.whatsappbotservice.storefront;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerOtpCodeRepository extends JpaRepository<CustomerOtpCode, Long> {

    Optional<CustomerOtpCode> findFirstByPhoneNumberOrderByCreatedAtDesc(String phoneNumber);
}
