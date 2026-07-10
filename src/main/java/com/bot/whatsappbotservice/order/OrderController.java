package com.bot.whatsappbotservice.order;

import com.bot.whatsappbotservice.common.ApiResponse;
import com.bot.whatsappbotservice.order.dto.CreateOrderRequest;
import com.bot.whatsappbotservice.order.dto.OrderResponse;
import com.bot.whatsappbotservice.order.dto.OrderStatusHistoryResponse;
import com.bot.whatsappbotservice.order.dto.RecordPaymentRequest;
import com.bot.whatsappbotservice.order.dto.UpdateOrderStatusRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@PreAuthorize("hasAnyRole('VENDOR_ADMIN', 'VENDOR_STAFF')")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> create(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(orderService.createOrder(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.get(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.list(status, paymentStatus, fromDate, toDate, pageable)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
            @PathVariable Long id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.updateStatus(id, request)));
    }

    @PostMapping("/{id}/payment")
    public ResponseEntity<ApiResponse<OrderResponse>> recordPayment(
            @PathVariable Long id, @Valid @RequestBody RecordPaymentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.recordPayment(id, request)));
    }

    @PostMapping("/{id}/payment/refund")
    public ResponseEntity<ApiResponse<OrderResponse>> refundPayment(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.refundPayment(id)));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<ApiResponse<Page<OrderStatusHistoryResponse>>> history(
            @PathVariable Long id, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.history(id, pageable)));
    }
}
