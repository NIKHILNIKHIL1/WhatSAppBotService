package com.bot.whatsappbotservice.whatsapp;

import java.math.BigDecimal;

public record CartLine(Long productId, String productName, BigDecimal unitPrice, BigDecimal quantity) {
}
