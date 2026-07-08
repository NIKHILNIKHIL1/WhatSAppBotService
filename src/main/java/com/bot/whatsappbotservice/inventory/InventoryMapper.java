package com.bot.whatsappbotservice.inventory;

import com.bot.whatsappbotservice.inventory.dto.InventoryResponse;
import com.bot.whatsappbotservice.inventory.dto.InventoryTransactionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InventoryMapper {

    @Mapping(target = "productId", source = "product.id")
    InventoryResponse toResponse(Inventory inventory);

    @Mapping(target = "productId", source = "product.id")
    InventoryTransactionResponse toResponse(InventoryTransaction transaction);
}
