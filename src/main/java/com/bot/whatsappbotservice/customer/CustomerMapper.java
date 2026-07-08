package com.bot.whatsappbotservice.customer;

import com.bot.whatsappbotservice.customer.dto.CustomerResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

    CustomerResponse toResponse(Customer customer);
}
