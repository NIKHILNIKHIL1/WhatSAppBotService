package com.bot.whatsappbotservice.customer;

import com.bot.whatsappbotservice.common.ApiResponse;
import com.bot.whatsappbotservice.customer.dto.CreateCustomerRequest;
import com.bot.whatsappbotservice.customer.dto.CustomerResponse;
import com.bot.whatsappbotservice.customer.dto.UpdateCustomerRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
@PreAuthorize("hasAnyRole('VENDOR_ADMIN', 'VENDOR_STAFF')")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CustomerResponse>> create(@Valid @RequestBody CreateCustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(customerService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerResponse>> update(@PathVariable Long id,
                                                                 @Valid @RequestBody UpdateCustomerRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.update(id, request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.get(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<CustomerResponse>>> list(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.list(pageable)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> block(@PathVariable Long id) {
        customerService.block(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
