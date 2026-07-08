package com.bot.whatsappbotservice.order;

import com.bot.whatsappbotservice.order.dto.OrderItemResponse;
import com.bot.whatsappbotservice.order.dto.OrderResponse;
import com.bot.whatsappbotservice.order.dto.OrderStatusHistoryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "customerName", source = "customer.fullName")
    @Mapping(target = "customerPhoneNumber", source = "customer.phoneNumber")
    OrderResponse toResponse(OrderHeader order);

    @Mapping(target = "productId", source = "product.id")
    OrderItemResponse toResponse(OrderItem item);

    OrderStatusHistoryResponse toResponse(OrderStatusHistory history);
}
