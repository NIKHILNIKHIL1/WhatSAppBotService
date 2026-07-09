package com.bot.whatsappbotservice.order;

import com.bot.whatsappbotservice.audit.AuditAction;
import com.bot.whatsappbotservice.audit.AuditChannel;
import com.bot.whatsappbotservice.audit.AuditService;
import com.bot.whatsappbotservice.catalog.Product;
import com.bot.whatsappbotservice.catalog.ProductRepository;
import com.bot.whatsappbotservice.common.TenantContext;
import com.bot.whatsappbotservice.common.exception.BusinessRuleViolationException;
import com.bot.whatsappbotservice.common.exception.ResourceNotFoundException;
import com.bot.whatsappbotservice.customer.Customer;
import com.bot.whatsappbotservice.customer.CustomerRepository;
import com.bot.whatsappbotservice.inventory.InventoryService;
import com.bot.whatsappbotservice.inventory.InventoryTransactionType;
import com.bot.whatsappbotservice.inventory.dto.AdjustStockRequest;
import com.bot.whatsappbotservice.order.dto.CreateOrderRequest;
import com.bot.whatsappbotservice.order.dto.OrderItemRequest;
import com.bot.whatsappbotservice.order.dto.OrderResponse;
import com.bot.whatsappbotservice.order.dto.OrderStatusHistoryResponse;
import com.bot.whatsappbotservice.order.dto.UpdateOrderStatusRequest;
import com.bot.whatsappbotservice.order.event.OrderStatusChangedEvent;
import com.bot.whatsappbotservice.tenant.Tenant;
import com.bot.whatsappbotservice.tenant.TenantRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OrderService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ORDER_NUMBER_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final TenantRepository tenantRepository;
    private final InventoryService inventoryService;
    private final OrderMapper orderMapper;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(OrderRepository orderRepository, OrderStatusHistoryRepository orderStatusHistoryRepository,
                         CustomerRepository customerRepository, ProductRepository productRepository,
                         TenantRepository tenantRepository, InventoryService inventoryService,
                         OrderMapper orderMapper, AuditService auditService,
                         ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.tenantRepository = tenantRepository;
        this.inventoryService = inventoryService;
        this.orderMapper = orderMapper;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        if (StringUtils.hasText(request.idempotencyKey())) {
            var existing = orderRepository.findByIdempotencyKey(request.idempotencyKey());
            if (existing.isPresent()) {
                return orderMapper.toResponse(existing.get());
            }
        }

        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> ResourceNotFoundException.of("Customer", request.customerId()));

        OrderHeader order = new OrderHeader();
        order.setOrderNumber(generateOrderNumber());
        order.setCustomer(customer);
        order.setStatus(OrderStatus.NEW);
        order.setChannel(request.channel() != null ? request.channel() : OrderChannel.API);
        order.setCurrencyCode(resolveTenantCurrency());
        order.setNotes(request.notes());
        order.setIdempotencyKey(request.idempotencyKey());

        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderItemRequest itemRequest : request.items()) {
            Product product = productRepository.findById(itemRequest.productId())
                    .filter(Product::isActive)
                    .orElseThrow(() -> ResourceNotFoundException.of("Product", itemRequest.productId()));

            BigDecimal lineTotal = product.getPrice().multiply(itemRequest.quantity());
            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setProductNameSnapshot(product.getName());
            item.setUnitPriceSnapshot(product.getPrice());
            item.setQuantity(itemRequest.quantity());
            item.setLineTotal(lineTotal);
            order.addItem(item);

            subtotal = subtotal.add(lineTotal);
        }
        order.setSubtotalAmount(subtotal);
        order.setTotalAmount(subtotal);

        // Insert first (IDENTITY generation makes order.getId() available immediately) so stock
        // deductions below can reference the order id, and so the whole thing rolls back together
        // if any line item has insufficient stock.
        order = orderRepository.save(order);

        recordStatusHistory(order, null, OrderStatus.NEW, "Order created");

        for (OrderItem item : order.getItems()) {
            AdjustStockRequest deduction = new AdjustStockRequest(
                    InventoryTransactionType.SALE, item.getQuantity().negate(), "ORDER", order.getId(),
                    "Order " + order.getOrderNumber());
            inventoryService.adjustStock(item.getProduct().getId(), deduction);
        }

        auditService.record("OrderHeader", order.getId().toString(), AuditAction.CREATE, null,
                Map.of("orderNumber", order.getOrderNumber(), "status", order.getStatus(),
                        "totalAmount", order.getTotalAmount()),
                AuditChannel.valueOf(order.getChannel().name()));

        return orderMapper.toResponse(order);
    }

    @Transactional
    public OrderResponse updateStatus(Long orderId, UpdateOrderStatusRequest request) {
        OrderHeader order = getOrThrow(orderId);
        OrderStatus from = order.getStatus();
        OrderStatus to = request.status();

        if (!from.canTransitionTo(to)) {
            throw new BusinessRuleViolationException("Cannot transition order from " + from + " to " + to);
        }

        order.setStatus(to);
        orderRepository.save(order);
        recordStatusHistory(order, from, to, request.notes());

        if (to == OrderStatus.CANCELLED) {
            for (OrderItem item : order.getItems()) {
                AdjustStockRequest release = new AdjustStockRequest(
                        InventoryTransactionType.RETURN, item.getQuantity(), "ORDER", order.getId(),
                        "Order " + order.getOrderNumber() + " cancelled");
                inventoryService.adjustStock(item.getProduct().getId(), release);
            }
        }

        auditService.record("OrderHeader", order.getId().toString(), AuditAction.UPDATE,
                Map.of("status", from), Map.of("status", to), AuditChannel.API);
        eventPublisher.publishEvent(new OrderStatusChangedEvent(TenantContext.getTenantId(), order.getId(), from, to));

        return orderMapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        return orderMapper.toResponse(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> list(OrderStatus status, Pageable pageable) {
        Page<OrderHeader> page = status != null
                ? orderRepository.findByStatus(status, pageable)
                : orderRepository.findAll(pageable);
        return page.map(orderMapper::toResponse);
    }

    /** Storefront-facing: a customer's own orders only, never another customer's or the full
     * tenant list — {@link #list} stays vendor-facing and untouched. */
    @Transactional(readOnly = true)
    public Page<OrderResponse> listForCustomer(Long customerId, Pageable pageable) {
        return orderRepository.findByCustomerId(customerId, pageable).map(orderMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<OrderStatusHistoryResponse> history(Long orderId, Pageable pageable) {
        getOrThrow(orderId);
        return orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtDesc(orderId, pageable)
                .map(orderMapper::toResponse);
    }

    private void recordStatusHistory(OrderHeader order, OrderStatus from, OrderStatus to, String notes) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setNotes(notes);
        orderStatusHistoryRepository.save(history);
    }

    private String resolveTenantCurrency() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return "INR";
        }
        return tenantRepository.findById(tenantId).map(Tenant::getCurrencyCode).orElse("INR");
    }

    private String generateOrderNumber() {
        String datePart = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE);
        StringBuilder suffix = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            suffix.append(ORDER_NUMBER_ALPHABET.charAt(RANDOM.nextInt(ORDER_NUMBER_ALPHABET.length())));
        }
        return "ORD-" + datePart + "-" + suffix;
    }

    private OrderHeader getOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", id));
    }
}
