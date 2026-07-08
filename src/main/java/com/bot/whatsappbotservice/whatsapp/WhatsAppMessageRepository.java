package com.bot.whatsappbotservice.whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WhatsAppMessageRepository extends JpaRepository<WhatsAppMessage, Long> {

    boolean existsByWaMessageId(String waMessageId);
}
